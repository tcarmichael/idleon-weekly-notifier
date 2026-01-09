variable "project_id" {
  description = "The Google Cloud Project ID"
  type        = string
}

variable "region" {
  description = "The Google Cloud Region"
  type        = string
  default     = "us-east1"
}

variable "service_name" {
  description = "The Cloud Run service name"
  type        = string
  default     = "idleon-bot"
}

variable "image_tag" {
  description = "The Docker image tag to deploy"
  type        = string
  default     = "latest" // Defaults to latest if not specified, though usually exact SHA is better
}

variable "discord_public_key" {
  description = "The Discord Public Key for verifying interactions"
  type        = string
  sensitive   = true
  default     = "" # Optional at init, but recommended to set
}

variable "sheets_api_key" {
  description = "Google Sheets API Key"
  type        = string
  sensitive   = true
  default     = "" # Optional at init, but recommended to set
}
