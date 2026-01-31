# Automated Translation Workflow for ArchiveTune

This document describes the automated translation workflow that pulls translations from Crowdin and uses Google Translate as a fallback for missing strings.

## Overview

The workflow automatically:
1. Downloads translations from Crowdin for Japanese (ja), Korean (ko), and Vietnamese (vi)
2. Identifies missing translations in the pulled files
3. Uses Google Cloud Translation API to auto-translate missing strings from English
4. Adds machine-translated strings with `<!-- MT fallback -->` comment markers
5. Commits and pushes changes to the `dev` branch

## Required GitHub Secrets

You need to configure the following secrets in your GitHub repository settings:

### 1. CROWDIN_TOKEN
- **Description**: Crowdin Personal Access Token
- **How to get**:
  1. Go to [Crowdin Account Settings](https://crowdin.com/settings#api-key)
  2. Generate a new Personal Access Token
  3. Copy the token
- **Permissions needed**: Project access for ArchiveTune project

### 2. CROWDIN_PROJECT_ID
- **Description**: Crowdin Project ID
- **How to get**:
  1. Go to your Crowdin project page
  2. Find the Project ID in the URL or project settings
  3. Example: `https://crowdin.com/project/PROJECT_ID`

### 3. GOOGLE_TRANSLATE_API_KEY
- **Description**: Google Cloud Translation API key
- **How to get**:
  1. Go to [Google Cloud Console](https://console.cloud.google.com/)
  2. Create a new project or select existing one
  3. Enable the Cloud Translation API
  4. Go to API & Services > Credentials
  5. Create credentials > API key
  6. Restrict the API key to only allow Cloud Translation API
- **Important**: You need to enable billing for this API to work

## Setting Up Secrets in GitHub

1. Go to your repository on GitHub
2. Navigate to **Settings** > **Secrets and variables** > **Actions**
3. Click **New repository secret**
4. Add each secret:
   - Name: `CROWDIN_TOKEN`
   - Value: Your Crowdin Personal Access Token
   - Click **Add secret**
5. Repeat for `CROWDIN_PROJECT_ID` and `GOOGLE_TRANSLATE_API_KEY`

## Workflow Triggers

### Automatic Trigger
The workflow runs automatically when:
- Changes are pushed to the `dev` branch
- The changes modify either:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values/archivetune_strings.xml`

### Manual Trigger
You can manually trigger the workflow:
1. Go to **Actions** tab in GitHub
2. Select "Sync Translations from Crowdin" workflow
3. Click **Run workflow**
4. Optionally specify languages (default: `ja,ko,vi`)
5. Click **Run workflow** button

## Workflow Steps

1. **Checkout repository** - Clones the codebase
2. **Set up Python** - Installs Python 3.x
3. **Install dependencies** - Installs required Python packages:
   - `google-cloud-translate` - Google Cloud Translation API client
   - `lxml` - XML processing library
4. **Download translations from Crowdin** - Pulls translations using Crowdin CLI
5. **Run machine translation fallback** - Processes missing translations
6. **Check for changes** - Detects if any files were modified
7. **Commit translation updates** - Creates a commit if changes exist
8. **Push to dev branch** - Pushes the commit to dev branch

## Language Mapping

| Language | Android Code | Crowdin Code |
|----------|-------------|--------------|
| Japanese | `ja` | `ja` |
| Korean | `ko` | `ko` |
| Vietnamese | `vi` | `vi` |

## String Files Processed

The workflow processes these two files:
1. `app/src/main/res/values/strings.xml` - Upstream InnerTune strings
2. `app/src/main/res/values/archivetune_strings.xml` - ArchiveTune-specific strings

For each language, it updates:
- `app/src/main/res/values-{lang}/strings.xml`
- `app/src/main/res/values/{lang}/archivetune_strings.xml`

## Machine Translation Fallback Logic

The MT fallback script:
1. Parses the English source XML files
2. Parses the target language XML files
3. Compares strings between source and target
4. For each missing or empty string in the target:
   - Calls Google Translate API
   - Translates from English to target language
   - Inserts the translation with `<!-- MT fallback -->` comment
5. Preserves all existing manual translations from Crowdin
6. Validates XML files are well-formed
7. Provides detailed statistics

## Commit Messages

The workflow creates commits with this format:
```
chore: sync translations from Crowdin + MT fallback [ci skip]

Synced translations for: ja, ko, vi
Includes machine translation fallback for missing strings
```

The `[ci skip]` tag prevents this commit from triggering other workflows.

## Error Handling

The workflow handles various error scenarios:

- **Crowdin API failures**: Logs error and continues without crashing
- **Google Translate API failures**: Logs specific translation errors, continues processing other strings
- **XML parsing errors**: Validates XML before and after modifications
- **Missing language directories**: Skips languages that don't exist
- **No API key configured**: Runs Crowdin sync without MT fallback

## Statistics Summary

After each run, the workflow outputs:
- Total strings processed
- Machine translations added
- Already translated (skipped)
- Errors encountered
- Files modified

## Testing

### Local Testing

To test the MT fallback script locally:

```bash
# Install dependencies
pip install google-cloud-translate lxml

# Set environment variables
export GOOGLE_TRANSLATE_API_KEY="your-api-key"
export LANGUAGES="ja,ko,vi"

# Run the script
python .github/scripts/mt_fallback.py
```

### Testing API Keys

1. **Test Crowdin Token**:
   ```bash
   curl -H "Authorization: Bearer YOUR_TOKEN" \
     https://api.crowdin.com/api/v2/user
   ```

2. **Test Google Translate API**:
   ```bash
   curl -X POST \
     "https://translation.googleapis.com/language/translate/v2?key=YOUR_KEY" \
     -H "Content-Type: application/json" \
     -d '{"q":"Hello","target":"ja"}'
   ```

## Troubleshooting

### Workflow fails at Crowdin step
- Verify `CROWDIN_TOKEN` and `CROWDIN_PROJECT_ID` are correct
- Check token has proper permissions
- Ensure Crowdin project is properly configured

### MT fallback not working
- Verify `GOOGLE_TRANSLATE_API_KEY` is set
- Check Google Cloud billing is enabled
- Verify API key has Cloud Translation API enabled
- Check API key restrictions allow your repository's IP

### XML validation errors
- Check workflow logs for specific XML parsing errors
- Ensure XML files have proper encoding (UTF-8)
- Verify XML syntax is correct

### No changes committed
- This is normal if all translations are already complete
- Check workflow summary to see statistics
- Manually trigger workflow to force sync

## Cost Considerations

### Crowdin
- Free tier available for open-source projects
- No additional costs for using the API

### Google Translate API
- Paid service with free tier (500,000 characters/month)
- Pricing varies by language and usage
- Monitor usage in Google Cloud Console to avoid unexpected charges
- Consider setting budget alerts

## Optional Enhancements

To extend the workflow functionality, you could:

1. **Add more languages** - Modify the `LANGUAGES` input or default value
2. **Create pull requests** - Change workflow to create PRs instead of direct commits
3. **Add workflow summary** - Include detailed statistics in GitHub Actions summary
4. **Rate limiting** - Implement delays between API calls to avoid quota issues
5. **Quality checks** - Add validation for translation quality
6. **Notifications** - Add Slack or email notifications on completion
7. **Dry-run mode** - Add option to preview changes without committing

## Support

For issues or questions:
1. Check workflow logs in the Actions tab
2. Review this documentation
3. Check Crowdin and Google Cloud documentation
4. Open an issue in the repository with details
