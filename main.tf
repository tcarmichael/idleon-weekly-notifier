terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

data "google_project" "project" {
}

# 1. Enable Services
resource "google_project_service" "run_api" {
  service            = "run.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "cloudbuild_api" {
  service            = "cloudbuild.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "firestore_api" {
  service            = "firestore.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "scheduler_api" {
  service            = "cloudscheduler.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "sheets_api" {
  service            = "sheets.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "secretmanager_api" {
  service            = "secretmanager.googleapis.com"
  disable_on_destroy = false
}

# Secrets Configuration
resource "google_secret_manager_secret" "discord_public_key" {
  secret_id = "discord-public-key-${var.service_name}"
  replication {
    auto {}
  }
  depends_on = [google_project_service.secretmanager_api]
}

resource "google_secret_manager_secret_version" "discord_public_key" {
  secret      = google_secret_manager_secret.discord_public_key.id
  secret_data = var.discord_public_key
}

resource "google_secret_manager_secret" "sheets_api_key" {
  secret_id = "sheets-api-key-${var.service_name}"
  replication {
    auto {}
  }
  depends_on = [google_project_service.secretmanager_api]
}

resource "google_secret_manager_secret_version" "sheets_api_key" {
  secret      = google_secret_manager_secret.sheets_api_key.id
  secret_data = var.sheets_api_key
}

# Grant Secret Accessor to Cloud Run Service Account (Default Compute SA)
resource "google_secret_manager_secret_iam_member" "discord_key_access" {
  secret_id = google_secret_manager_secret.discord_public_key.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${data.google_project.project.number}-compute@developer.gserviceaccount.com"
}

resource "google_secret_manager_secret_iam_member" "sheets_key_access" {
  secret_id = google_secret_manager_secret.sheets_api_key.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${data.google_project.project.number}-compute@developer.gserviceaccount.com"
}

# 2. Cloud Run Service
resource "google_cloud_run_service" "idleon_bot" {
  name     = var.service_name
  location = var.region

  template {
    spec {
      containers {
        image = "us-east1-docker.pkg.dev/${var.project_id}/idleon-bot-repo/idleon-bot:${var.image_tag}"
        
        env {
          name  = "PROJECT_ID"
          value = var.project_id
        }
        
        # Only add these if they are provided, otherwise we might rely on them being ignored or manually managed
        # However, for a clean IaC, we should manage them.
        # We use a trick: if variable is empty, we don't include the block? 
        # Terraform doesn't support conditional blocks easily inside lists without dynamic blocks.
        
        env {
          name = "DISCORD_PUBLIC_KEY"
          value_from {
            secret_key_ref {
              name = google_secret_manager_secret.discord_public_key.secret_id
              key  = "latest"
            }
          }
        }

        env {
          name = "SHEETS_API_KEY"
          value_from {
            secret_key_ref {
              name = google_secret_manager_secret.sheets_api_key.secret_id
              key  = "latest"
            }
          }
        }
      }
    }
  }

  traffic {
    percent         = 100
    latest_revision = true
  }

  depends_on = [google_project_service.run_api]
  
  # Ignore changes to environment variables if you want to manage them outside of Terraform (e.g. manually in console)
  # But better to encourage using tfvars. 
  # lifecycle {
  #   ignore_changes = [template[0].spec[0].containers[0].env]
  # }
}

# Allow unauthenticated invocations (public access)
resource "google_cloud_run_service_iam_member" "all_users" {
  service  = google_cloud_run_service.idleon_bot.name
  location = google_cloud_run_service.idleon_bot.location
  role     = "roles/run.invoker"
  member   = "allUsers"
}

# 3. Cloud Scheduler Job
resource "google_cloud_scheduler_job" "idleon_weekly_notify" {
  name             = "idleon-weekly-notify-${var.service_name}"
  description      = "Weekly Idleon Notification Trigger"
  schedule         = "0 18 * * 2" # Every Tuesday at 18:00
  time_zone        = "America/New_York"
  attempt_deadline = "30s"

  http_target {
    http_method = "POST"
    uri         = "${google_cloud_run_service.idleon_bot.status[0].url}/cron"
  }

  depends_on = [google_project_service.scheduler_api]
}
