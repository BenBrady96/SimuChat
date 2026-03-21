# =============================================================================
# SimuChat — Terraform Configuration
# =============================================================================
# Provisions a GCP e2-small Ubuntu VM with K3s (lightweight Kubernetes)
# pre-installed via a startup script. Network resources include a custom
# VPC, subnet, and firewall rules for SSH, HTTP, and HTTPS access.
# =============================================================================

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

# -----------------------------------------------------------------------------
# Provider Configuration
# -----------------------------------------------------------------------------
provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}

# -----------------------------------------------------------------------------
# VPC Network
# -----------------------------------------------------------------------------
# Custom VPC with no auto-created subnets — we define our own for full control.
resource "google_compute_network" "simuchat_vpc" {
  name                    = "simuchat-vpc"
  auto_create_subnetworks = false
  description             = "Custom VPC for the SimuChat application"
}

# -----------------------------------------------------------------------------
# Subnet
# -----------------------------------------------------------------------------
# A /24 subnet providing 254 usable IPs — more than enough for a single-node
# K3s cluster, with room for future expansion.
resource "google_compute_subnetwork" "simuchat_subnet" {
  name          = "simuchat-subnet"
  ip_cidr_range = "10.0.1.0/24"
  region        = var.region
  network       = google_compute_network.simuchat_vpc.id
  description   = "Primary subnet for SimuChat workloads"
}

# -----------------------------------------------------------------------------
# Firewall Rules
# -----------------------------------------------------------------------------
# Allow SSH (22) for remote management, HTTP (80) and HTTPS (443) for web
# traffic. In production, you would restrict source ranges to known IPs.
resource "google_compute_firewall" "simuchat_allow_web" {
  name    = "simuchat-allow-web"
  network = google_compute_network.simuchat_vpc.name

  allow {
    protocol = "tcp"
    ports    = ["22", "80", "443"]
  }

  # WARNING: 0.0.0.0/0 allows access from anywhere. Restrict in production.
  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["simuchat-server"]
  description   = "Allow SSH, HTTP, and HTTPS traffic to SimuChat instances"
}

# -----------------------------------------------------------------------------
# Compute Instance (K3s Node)
# -----------------------------------------------------------------------------
# An e2-small instance running Ubuntu 22.04 LTS. The startup script
# automatically installs K3s, a lightweight Kubernetes distribution ideal
# for single-node deployments and resource-constrained environments.
resource "google_compute_instance" "simuchat_vm" {
  name         = "simuchat-k3s-node"
  machine_type = var.machine_type
  tags         = ["simuchat-server"]

  boot_disk {
    initialize_params {
      image = "ubuntu-os-cloud/ubuntu-2204-lts"
      size  = 30 # GB — sufficient for OS, Docker images, and K3s data
      type  = "pd-standard"
    }
  }

  network_interface {
    subnetwork = google_compute_subnetwork.simuchat_subnet.id

    # Assign an ephemeral public IP for external access
    access_config {
      // Ephemeral public IP
    }
  }

  # ---------------------------------------------------------------------------
  # K3s Bootstrap Script
  # ---------------------------------------------------------------------------
  # This script runs once on first boot. It installs K3s in single-server
  # mode with Traefik disabled (we use our own Ingress config).
  metadata_startup_script = <<-EOF
    #!/bin/bash
    set -euo pipefail

    echo "=== SimuChat K3s Bootstrap ==="

    # Update system packages
    apt-get update -y && apt-get upgrade -y

    # Install K3s (lightweight Kubernetes) in single-server mode
    # --disable traefik: We manage our own Ingress resources
    # --write-kubeconfig-mode 644: Makes kubeconfig readable for kubectl
    curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="server \
      --disable traefik \
      --write-kubeconfig-mode 644" sh -

    # Wait for K3s to become ready
    echo "Waiting for K3s to initialise..."
    sleep 30
    kubectl wait --for=condition=Ready node --all --timeout=120s

    echo "=== K3s bootstrap complete ==="
  EOF

  metadata = {
    # Enable OS Login for SSH key management via IAM
    enable-oslogin = "TRUE"
  }

  service_account {
    scopes = ["cloud-platform"]
  }
}
