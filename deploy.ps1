# deploy.ps1
# Usage: .\deploy.ps1 -ProjectId "your-project-id" -Region "us-central1"

param(
    [string]$ProjectId = "idleon-weekly-notifier",
    [string]$Region = "us-east1"
)

Write-Host "Deploying Idleon Weekly Notifier to $ProjectId in $Region..."

# 1. Enable Services
Write-Host "Enabling APIs..."
gcloud services enable run.googleapis.com cloudbuild.googleapis.com firestore.googleapis.com cloudscheduler.googleapis.com sheets.googleapis.com --project $ProjectId

# 2. Build and Push Container using Cloud Build
Write-Host "Submitting build to Cloud Build..."
# Note: This uses the Dockerfile in the current directory
gcloud builds submit --tag "gcr.io/$ProjectId/idleon-bot" --project $ProjectId

# 3. Deploy to Cloud Run
Write-Host "Deploying to Cloud Run..."
# We allow unauthenticated invocations for the interaction endpoint (validated by signature)
# but we might want to restrict it if possible. Discord needs public access.
gcloud run deploy idleon-bot `
    --image "gcr.io/$ProjectId/idleon-bot" `
    --platform managed `
    --region $Region `
    --project $ProjectId `
    --allow-unauthenticated `
    --set-env-vars="PROJECT_ID=$ProjectId"

# 4. Get the URL
$ServiceUrl = gcloud run services describe idleon-bot --platform managed --region $Region --project $ProjectId --format 'value(status.url)'
Write-Host "Service Deployed at: $ServiceUrl"
Write-Host "IMPORTANT: Configure this URL + '/interactions' in your Discord Developer Portal."

# 5. Create/Update Cron Job
# Every Tuesday at 6:00PM EST.
# Cron format (Unix): Minute Hour DayOfMonth Month DayOfWeek
# 6:00 PM EST = 18:00 EST. 
# Note: Cloud Scheduler uses timezone.
Write-Host "Setting up Cloud Scheduler..."
$CronSchedule = "0 18 * * 2" # Every Tuesday at 18:00
gcloud scheduler jobs create http idleon-weekly-notify `
    --schedule "$CronSchedule" `
    --time-zone "America/New_York" `
    --uri "$ServiceUrl/cron" `
    --http-method POST `
    --project $ProjectId `
    --location $Region `
    --attempt-deadline 30s `
    --quiet
    
# If it exists, update it
if ($LASTEXITCODE -ne 0) {
    Write-Host "Job might already exist, updating..."
    gcloud scheduler jobs update http idleon-weekly-notify `
    --schedule "$CronSchedule" `
    --time-zone "America/New_York" `
    --uri "$ServiceUrl/cron" `
    --project $ProjectId `
    --location $Region
}

Write-Host "Deployment Complete!"
Write-Host "Don't forget to set DISCORD_PUBLIC_KEY and SHEETS_API_KEY environment variables in Cloud Run if needed via:"
Write-Host "gcloud run services update idleon-bot --update-env-vars=DISCORD_PUBLIC_KEY=...,SHEETS_API_KEY=... --region $Region"
