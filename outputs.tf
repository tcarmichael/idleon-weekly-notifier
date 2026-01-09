output "service_url" {
  description = "The URL of the deployed Cloud Run service"
  value       = google_cloud_run_service.idleon_bot.status[0].url
}

output "scheduler_job_name" {
    description = "The name of the scheduler job"
    value = google_cloud_scheduler_job.idleon_weekly_notify.name
}
