terraform {
  backend "gcs" {
    bucket  = "idleon-weekly-notifier-tf-state"
    prefix  = "terraform/state"
  }
}
