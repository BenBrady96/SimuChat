# =============================================================================
# SimuChat — Terraform Variables
# =============================================================================
# Define input variables for the SimuChat infrastructure. Override these
# values in terraform.tfvars or via CLI flags (-var="key=value").
# =============================================================================

variable "project_id" {
  description = "The GCP project ID to deploy resources into"
  type        = string
}

variable "region" {
  description = "The GCP region for networking resources"
  type        = string
  default     = "europe-west2" # London — low latency for UK users
}

variable "zone" {
  description = "The GCP zone for the compute instance"
  type        = string
  default     = "europe-west2-a"
}

variable "machine_type" {
  description = "GCP machine type for the K3s node (e2-small: 2 vCPUs, 2GB RAM)"
  type        = string
  default     = "e2-small"
}
