# =============================================================================
# SimuChat — Terraform Outputs
# =============================================================================
# Display useful information after 'terraform apply' completes.
# =============================================================================

output "vm_external_ip" {
  description = "The public IP address of the SimuChat K3s node"
  value       = google_compute_instance.simuchat_vm.network_interface[0].access_config[0].nat_ip
}

output "ssh_command" {
  description = "Command to SSH into the SimuChat VM"
  value       = "gcloud compute ssh ${google_compute_instance.simuchat_vm.name} --zone=${var.zone}"
}

output "kubectl_config" {
  description = "Command to copy the K3s kubeconfig from the VM"
  value       = "gcloud compute scp ${google_compute_instance.simuchat_vm.name}:/etc/rancher/k3s/k3s.yaml ./k3s.yaml --zone=${var.zone}"
}
