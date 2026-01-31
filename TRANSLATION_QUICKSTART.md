# Quick Start: Translation Workflow Setup

Get the ArchiveTune automated translation workflow running in 5 minutes.

## Step 1: Get API Keys (5 minutes)

### Crowdin Token
1. Go to [crowdin.com/settings#api-key](https://crowdin.com/settings#api-key)
2. Click "New token"
3. Copy the token

### Crowdin Project ID
1. Go to your Crowdin project
2. Copy the Project ID from the URL

### Google Translate API Key
1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Search "Cloud Translation API" and enable it
3. Go to APIs & Services → Credentials → Create Credentials → API key
4. Restrict key to "Cloud Translation API" only
5. Copy the API key

## Step 2: Configure GitHub Secrets (2 minutes)

1. Go to your repository → Settings → Secrets and variables → Actions
2. Add these secrets:

```
Name: CROWDIN_TOKEN
Value: <your Crowdin token from Step 1>

Name: CROWDIN_PROJECT_ID
Value: <your Project ID from Step 1>

Name: GOOGLE_TRANSLATE_API_KEY
Value: <your Google API key from Step 1>
```

## Step 3: Test the Workflow (1 minute)

1. Go to the **Actions** tab in your repository
2. Select "Sync Translations from Crowdin"
3. Click "Run workflow" → "Run workflow"
4. Wait 2-3 minutes for it to complete

## That's it! 🎉

Your workflow is now set up. It will:
- Automatically run when you push string changes to dev branch
- Download translations from Crowdin
- Fill in missing strings with Google Translate
- Commit changes back to dev branch

## Need Help?

- **Full documentation**: [`.github/workflows/TRANSLATION_WORKFLOW.md`](.github/workflows/TRANSLATION_WORKFLOW.md)
- **Detailed setup guide**: [`TRANSLATION_SETUP.md`](TRANSLATION_SETUP.md)
- **Setup checklist**: [`TRANSLATION_CHECKLIST.md`](TRANSLATION_CHECKLIST.md)
- **Validation script**: Run `python .github/scripts/test_setup.py`

## What's Next?

1. Monitor the first workflow run
2. Check translation quality
3. Review the workflow summary
4. Share with your team
