#!/usr/bin/env python3
"""
Quick test script to validate the translation workflow setup
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

def test_file_exists(filepath, description):
    """Test if a file exists"""
    path = Path(filepath)
    if path.exists():
        print(f"✓ {description}: {filepath}")
        return True
    else:
        print(f"✗ {description}: {filepath} (NOT FOUND)")
        return False

def test_xml_parsing(filepath):
    """Test if XML file is well-formed"""
    try:
        ET.parse(filepath)
        print(f"✓ XML is well-formed: {filepath}")
        return True
    except ET.ParseError as e:
        print(f"✗ XML parsing failed for {filepath}: {e}")
        return False

def test_python_syntax(filepath):
    """Test if Python file has valid syntax"""
    try:
        with open(filepath, 'r') as f:
            compile(f.read(), filepath, 'exec')
        print(f"✓ Python syntax is valid: {filepath}")
        return True
    except SyntaxError as e:
        print(f"✗ Python syntax error in {filepath}: {e}")
        return False

def main():
    print("=" * 60)
    print("Translation Workflow Setup Validation")
    print("=" * 60)
    print()

    # Test workflow file
    workflow_file = ".github/workflows/crowdin-sync.yml"
    test_file_exists(workflow_file, "Workflow file")

    # Test MT fallback script
    script_file = ".github/scripts/mt_fallback.py"
    test_file_exists(script_file, "MT fallback script")
    test_python_syntax(script_file)

    # Test documentation
    docs_file = ".github/workflows/TRANSLATION_WORKFLOW.md"
    test_file_exists(docs_file, "Documentation file")

    # Test requirements
    reqs_file = ".github/scripts/requirements.txt"
    test_file_exists(reqs_file, "Requirements file")

    # Test Crowdin config
    crowdin_file = "crowdin.yml"
    test_file_exists(crowdin_file, "Crowdin configuration")

    print()
    print("-" * 60)
    print("Testing XML Files")
    print("-" * 60)

    # Test source XML files
    test_xml_parsing("app/src/main/res/values/strings.xml")
    test_xml_parsing("app/src/main/res/values/archivetune_strings.xml")

    # Test target XML files
    for lang in ['ja', 'ko', 'vi']:
        test_xml_parsing(f"app/src/main/res/values-{lang}/strings.xml")
        test_xml_parsing(f"app/src/main/res/values-{lang}/archivetune_strings.xml")

    print()
    print("=" * 60)
    print("Validation Complete!")
    print("=" * 60)
    print()
    print("Next steps:")
    print("1. Configure GitHub secrets:")
    print("   - CROWDIN_TOKEN")
    print("   - CROWDIN_PROJECT_ID")
    print("   - GOOGLE_TRANSLATE_API_KEY")
    print()
    print("2. See .github/workflows/TRANSLATION_WORKFLOW.md for details")
    print()

    return 0

if __name__ == '__main__':
    sys.exit(main())
