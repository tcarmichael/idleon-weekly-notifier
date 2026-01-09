resource "google_project_service" "cloudresourcemanager_api" {
  service            = "cloudresourcemanager.googleapis.com"
  disable_on_destroy = false
}
