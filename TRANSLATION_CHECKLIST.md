# Translation Workflow Setup Checklist

Use this checklist to verify your translation workflow setup is complete and working.

## ✅ Pre-Setup Requirements

- [ ] You have a Crowdin account
- [ ] You have a Google Cloud account with billing enabled
- [ ] You have admin access to the GitHub repository
- [ ] You have the `crowdin.yml` file in the repository root (should already exist)

## ✅ File Verification

Run this to verify all files are in place:
```bash
python .github/scripts/test_setup.py
```

Expected output:
- [ ] ✓ Workflow file: .github/workflows/crowdin-sync.yml
- [ ] ✓ MT fallback script: .github/scripts/mt_fallback.py
- [ ] ✓ Python syntax is valid
- [ ] ✓ Documentation file: .github/workflows/TRANSLATION_WORKFLOW.md
- [ ] ✓ Requirements file: .github/scripts/requirements.txt
- [ ] ✓ Crowdin configuration: crowdin.yml
- [ ] ✓ All XML files are well-formed

## ✅ GitHub Secrets Configuration

Go to: **Repository Settings** → **Secrets and variables** → **Actions**

### Crowdin Token
- [ ] Secret name: `CROWDIN_TOKEN`
- [ ] Secret value: Your Crowdin Personal Access Token
- [ ] Test: `curl -H "Authorization: Bearer YOUR_TOKEN" https://api.crowdin.com/api/v2/user`

### Crowdin Project ID
- [ ] Secret name: `CROWDIN_PROJECT_ID`
- [ ] Secret value: Your Crowdin project ID
- [ ] Test: Check your Crowdin project URL: `https://crowdin.com/project/PROJECT_ID`

### Google Translate API Key
- [ ] Secret name: `GOOGLE_TRANSLATE_API_KEY`
- [ ] Secret value: Your Google Cloud Translation API key
- [ ] Test: `curl -X POST "https://translation.googleapis.com/language/translate/v2?key=YOUR_KEY" -H "Content-Type: application/json" -d '{"q":"Hello","target":"ja"}'`

## ✅ Crowdin Project Setup

- [ ] Crowdin project created/selected
- [ ] Target languages enabled: Japanese (ja), Korean (ko), Vietnamese (vi)
- [ ] Source files mapped correctly in `crowdin.yml`:
  - [ ] `/app/src/main/res/values/strings.xml`
  - [ ] `/app/src/main/res/values/archivetune_strings.xml`
- [ ] Translation files configured with `%android_code%` pattern:
  - [ ] `/app/src/main/res/values-%android_code%/strings.xml`
  - [ ] `/app/src/main/res/values-%android_code%/archivetune_strings.xml`

## ✅ Google Cloud Setup

- [ ] Google Cloud project created/selected
- [ ] Cloud Translation API enabled
- [ ] API key created
- [ ] API key restricted to Cloud Translation API only
- [ ] Budget alerts set up (recommended)
- [ ] Billing enabled

## ✅ Workflow Testing

### Manual Trigger Test
- [ ] Go to **Actions** tab in GitHub
- [ ] Select "Sync Translations from Crowdin" workflow
- [ ] Click **Run workflow**
- [ ] Click green **Run workflow** button
- [ ] Wait for workflow to complete
- [ ] Check workflow logs for errors
- [ ] Verify workflow summary shows completion

### Automatic Trigger Test
- [ ] Make a small change to `app/src/main/res/values/strings.xml`
  - Example: Add a new string like `<string name="test_string">Test</string>`
- [ ] Commit the change
- [ ] Push to `dev` branch
- [ ] Wait for workflow to trigger automatically
- [ ] Check workflow runs in Actions tab
- [ ] Verify workflow completes successfully
- [ ] Check if changes were committed back to dev branch

## ✅ Verification Checks

After workflow runs:

### Check Workflow Logs
- [ ] No critical errors in logs
- [ ] Crowdin download step completed
- [ ] MT fallback script executed
- [ ] Changes detected (if applicable)
- [ ] Commit created (if changes)
- [ ] Push successful (if changes)

### Check Workflow Summary
- [ ] Languages processed listed correctly
- [ ] Trigger type shown correctly
- [ ] Status displayed correctly

### Check Commit History (if changes made)
- [ ] New commit appears in dev branch
- [ ] Commit message: "chore: sync translations from Crowdin + MT fallback [ci skip]"
- [ ] Translation files modified (if applicable)

### Check Translation Files (if MT fallback used)
- [ ] Open one of the target XML files
- [ ] Look for `<!-- MT fallback -->` comments
- [ ] Verify machine-translated strings are present
- [ ] Verify XML files are still well-formed

## ✅ Cost Monitoring

- [ ] Check Crowdin usage (should be free for open-source)
- [ ] Check Google Cloud Translation API usage
- [ ] Verify usage is within free tier (500,000 chars/month)
- [ ] Set up budget alerts if not done

## ✅ Documentation Review

- [ ] Read `.github/workflows/TRANSLATION_WORKFLOW.md`
- [ ] Read `TRANSLATION_SETUP.md`
- [ ] Understand error handling procedures
- [ ] Know how to troubleshoot common issues

## ✅ Security Verification

- [ ] No API keys committed to repository
- [ ] All credentials in GitHub Secrets
- [ ] Google API key restricted properly
- [ ] Crowdin token has minimal required permissions

## ✅ Success Criteria

Your setup is complete when ALL of these are true:

- [ ] Workflow runs without errors
- [ ] Translations download from Crowdin successfully
- [ ] Machine translation fallback works (if missing strings exist)
- [ ] Commits are created and pushed to dev branch
- [ ] XML files remain well-formed
- [ ] No unexpected costs incurred
- [ ] Team understands how to use the workflow

## 🐛 Troubleshooting Quick Reference

### Workflow doesn't trigger
- Check if you're pushing to `dev` branch
- Verify string files were modified
- Check workflow file syntax

### Crowdin download fails
- Verify `CROWDIN_TOKEN` is correct
- Verify `CROWDIN_PROJECT_ID` is correct
- Check Crowdin project permissions

### MT fallback not working
- Verify `GOOGLE_TRANSLATE_API_KEY` is set
- Check Google Cloud billing is enabled
- Verify API key has Cloud Translation API enabled

### No changes committed
- This is normal if all translations are complete
- Check workflow logs for statistics
- Manually trigger to force sync

### XML validation errors
- Check workflow logs for specific errors
- Verify UTF-8 encoding
- Check XML syntax

## 📞 Getting Help

If you encounter issues:

1. Check workflow logs in GitHub Actions tab
2. Review error messages carefully
3. Consult `.github/workflows/TRANSLATION_WORKFLOW.md`
4. Review `TRANSLATION_SETUP.md`
5. Check Crowdin and Google Cloud documentation
6. Open a GitHub issue with:
   - Workflow run link
   - Error messages
   - Steps to reproduce
   - Environment details

---

## 🎉 Setup Complete!

Once all items are checked off, your automated translation workflow is ready to use!

**Next steps:**
1. Monitor the first few workflow runs
2. Review translation quality
3. Adjust workflow configuration as needed
4. Share with team members

**For questions, see:**
- [TRANSLATION_SETUP.md](TRANSLATION_SETUP.md) - Setup instructions
- [TRANSLATION_WORKFLOW.md](.github/workflows/TRANSLATION_WORKFLOW.md) - Detailed documentation
- [TRANSLATION_CHANGES_SUMMARY.md](TRANSLATION_CHANGES_SUMMARY.md) - Implementation summary
