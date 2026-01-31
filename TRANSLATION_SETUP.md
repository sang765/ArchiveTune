# ArchiveTune Translation Setup Guide

## Quick Start

This guide helps you set up the automated translation workflow for ArchiveTune.

## Overview

The automated translation workflow:
- ✅ Downloads translations from Crowdin for Japanese (ja), Korean (ko), and Vietnamese (vi)
- ✅ Uses Google Translate as fallback for missing strings
- ✅ Commits changes to the dev branch automatically
- ✅ Runs on push to dev branch or manual trigger

## Prerequisites

1. **Crowdin Account**
   - Create an account at [crowdin.com](https://crowdin.com)
   - Create a project for ArchiveTune (or use existing one)
   - Note your Project ID

2. **Google Cloud Account**
   - Create an account at [console.cloud.google.com](https://console.cloud.google.com)
   - Enable billing (required for Translation API)
   - Enable Cloud Translation API

## Step-by-Step Setup

### 1. Get Crowdin API Token

1. Log in to [Crowdin](https://crowdin.com)
2. Go to **Account Settings** → **API** → **New token**
3. Create a token with these scopes:
   - `project`
   - `translation`
4. Copy the token (you'll need it for GitHub secrets)

### 2. Get Crowdin Project ID

1. Go to your Crowdin project page
2. The Project ID is in the URL: `https://crowdin.com/project/YOUR_PROJECT_ID`
3. Copy the Project ID

### 3. Get Google Translate API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Select or create a project
3. Navigate to **APIs & Services** → **Library**
4. Search for "Cloud Translation API" and enable it
5. Go to **APIs & Services** → **Credentials**
6. Click **Create Credentials** → **API key**
7. **Important**: Restrict the API key:
   - Application restrictions: None (or specific IP addresses)
   - API restrictions: Only "Cloud Translation API"
8. Copy the API key

### 4. Configure GitHub Secrets

1. Go to your GitHub repository
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add these secrets:

#### Secret: CROWDIN_TOKEN
- Name: `CROWDIN_TOKEN`
- Value: Your Crowdin Personal Access Token from Step 1

#### Secret: CROWDIN_PROJECT_ID
- Name: `CROWDIN_PROJECT_ID`
- Value: Your Crowdin Project ID from Step 2

#### Secret: GOOGLE_TRANSLATE_API_KEY
- Name: `GOOGLE_TRANSLATE_API_KEY`
- Value: Your Google Translate API key from Step 3

### 5. Configure Crowdin Project

Ensure your Crowdin project has:
- Files mapped according to `crowdin.yml`
- Target languages enabled: Japanese, Korean, Vietnamese
- File structure matches:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values/archivetune_strings.xml`

### 6. Test the Workflow

#### Option A: Manual Trigger
1. Go to **Actions** tab in GitHub
2. Select "Sync Translations from Crowdin"
3. Click **Run workflow**
4. Click green **Run workflow** button

#### Option B: Automatic Trigger
1. Make a change to `app/src/main/res/values/strings.xml` or `app/src/main/res/values/archivetune_strings.xml`
2. Push to `dev` branch
3. Workflow will run automatically

## Files Created

```
.github/
├── workflows/
│   ├── crowdin-sync.yml           # Main workflow file
│   └── TRANSLATION_WORKFLOW.md   # Detailed documentation
└── scripts/
    ├── mt_fallback.py             # Machine translation script
    ├── requirements.txt           # Python dependencies
    └── test_setup.py              # Setup validation script
```

## Testing Locally

To test the MT fallback script locally:

```bash
# Install dependencies
pip install -r .github/scripts/requirements.txt

# Set environment variables
export GOOGLE_TRANSLATE_API_KEY="your-api-key"
export LANGUAGES="ja,ko,vi"

# Run the script
python .github/scripts/mt_fallback.py
```

To validate the setup:

```bash
python .github/scripts/test_setup.py
```

## Workflow Behavior

### Automatic Triggers
- Runs when you push to `dev` branch
- Only triggers when string files change:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values/archivetune_strings.xml`

### Manual Triggers
- Can be triggered anytime from Actions tab
- Can specify custom languages
- Default: `ja,ko,vi`

### What Happens
1. Downloads translations from Crowdin
2. Processes missing translations with Google Translate
3. Validates XML files
4. Creates commit with message:
   ```
   chore: sync translations from Crowdin + MT fallback [ci skip]
   ```
5. Pushes to `dev` branch

## Machine Translation Fallback

The MT fallback:
- Only adds translations for missing strings
- Preserves all existing manual translations
- Marks machine-translated strings with `<!-- MT fallback -->`
- Validates XML before and after modifications
- Provides detailed statistics

## Cost Considerations

### Crowdin
- Free tier available for open-source projects
- No API costs for using the workflow

### Google Translate API
- Free tier: 500,000 characters/month
- Paid tier: $20 per 1 million characters
- Monitor usage in Google Cloud Console
- Set budget alerts to avoid unexpected charges

## Troubleshooting

### Workflow fails at Crowdin step
✅ Check `CROWDIN_TOKEN` and `CROWDIN_PROJECT_ID` are correct
✅ Verify token has proper permissions
✅ Ensure Crowdin project is configured correctly

### MT fallback not working
✅ Verify `GOOGLE_TRANSLATE_API_KEY` is set
✅ Check Google Cloud billing is enabled
✅ Verify API key has Cloud Translation API enabled
✅ Check API key restrictions

### No changes committed
✅ This is normal if all translations are complete
✅ Check workflow logs for statistics
✅ Manually trigger workflow to force sync

### XML validation errors
✅ Check workflow logs for specific errors
✅ Ensure files have UTF-8 encoding
✅ Verify XML syntax is correct

## Security Best Practices

1. **API Keys**
   - Never commit API keys to repository
   - Always use GitHub Secrets
   - Rotate keys regularly

2. **Google Cloud**
   - Restrict API key to specific APIs
   - Set budget alerts
   - Monitor usage regularly

3. **Crowdin**
   - Use least-privilege token
   - Rotate token if compromised
   - Monitor access logs

## Maintenance

### Regular Tasks
- Review translation quality
- Update Crowdin configuration as needed
- Monitor API usage and costs
- Review workflow logs for errors

### Scaling to More Languages
To add more languages:

1. Enable language in Crowdin project
2. Add to workflow default or use manual trigger
3. Update documentation

Example for manual trigger:
- Input: `ja,ko,vi,fr,de,es`

## Support

For issues:
1. Check workflow logs in Actions tab
2. Review [TRANSLATION_WORKFLOW.md](.github/workflows/TRANSLATION_WORKFLOW.md)
3. Check Crowdin and Google Cloud documentation
4. Open an issue with details

## Next Steps

After setup:
1. ✅ Test manual workflow trigger
2. ✅ Test automatic trigger by pushing string changes
3. ✅ Review first workflow run
4. ✅ Check translation quality
5. ✅ Monitor API usage

## Success Criteria

Your setup is successful when:
- ✅ Workflow runs without errors
- ✅ Translations are downloaded from Crowdin
- ✅ Missing strings are machine-translated
- ✅ Commits are created and pushed to dev
- ✅ XML files remain well-formed
- ✅ Statistics show expected results

---

For detailed documentation, see [`.github/workflows/TRANSLATION_WORKFLOW.md`](.github/workflows/TRANSLATION_WORKFLOW.md)
