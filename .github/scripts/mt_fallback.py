#!/usr/bin/env python3
"""
Machine Translation Fallback Script for ArchiveTune
Handles missing translations by using Google Cloud Translation API
"""

import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, Optional
import re

try:
    from google.cloud import translate_v2 as translate
except ImportError:
    print("Error: google-cloud-translate not installed")
    sys.exit(1)

# Language mapping for Google Translate API
LANGUAGE_CODES = {
    'ja': 'ja',
    'ko': 'ko',
    'vi': 'vi'
}

# String files to process
STRING_FILES = [
    'strings.xml',
    'archivetune_strings.xml'
]


class MTFallbackHandler:
    def __init__(self, api_key: Optional[str] = None):
        """Initialize the MT fallback handler with Google Translate client."""
        self.api_key = api_key
        self.translate_client = None
        self.stats = {
            'total_processed': 0,
            'mt_added': 0,
            'skipped': 0,
            'errors': 0
        }

        if api_key:
            try:
                self.translate_client = translate.Client(api_key=api_key)
                print("Google Translate API client initialized successfully")
            except Exception as e:
                print(f"Warning: Failed to initialize Google Translate client: {e}")
                print("Continuing without MT fallback...")

    def parse_string_file(self, file_path: Path) -> Dict[str, str]:
        """
        Parse Android string XML file and return a dictionary of name->value.
        """
        strings = {}
        try:
            tree = ET.parse(file_path)
            root = tree.getroot()

            for string_elem in root.findall('string'):
                name = string_elem.get('name')
                value = string_elem.text or ''
                strings[name] = value

            # Handle plurals
            for plurals_elem in root.findall('plurals'):
                name = plurals_elem.get('name')
                for item in plurals_elem.findall('item'):
                    quantity = item.get('quantity')
                    value = item.text or ''
                    key = f"{name}_{quantity}"
                    strings[key] = value

        except ET.ParseError as e:
            print(f"Error parsing {file_path}: {e}")
        except Exception as e:
            print(f"Error reading {file_path}: {e}")

        return strings

    def translate_text(self, text: str, target_lang: str) -> Optional[str]:
        """
        Translate text using Google Cloud Translation API.
        Returns None if translation fails.
        """
        if not self.translate_client:
            return None

        if not text or not text.strip():
            return text

        try:
            result = self.translate_client.translate(
                text,
                target_language=target_lang
            )
            return result['translatedText']
        except Exception as e:
            print(f"Error translating '{text[:50]}...' to {target_lang}: {e}")
            self.stats['errors'] += 1
            return None

    def add_missing_translations(
        self,
        source_file: Path,
        target_file: Path,
        target_lang: str
    ) -> bool:
        """
        Add missing translations to target file using machine translation.
        Returns True if file was modified.
        """
        print(f"\nProcessing {target_file.name}...")

        # Parse source and target files
        source_strings = self.parse_string_file(source_file)
        target_strings = self.parse_string_file(target_file)

        if not source_strings:
            print(f"Warning: No strings found in source file {source_file}")
            return False

        # Parse target file as XML for modification
        try:
            tree = ET.parse(target_file)
            root = tree.getroot()
        except ET.ParseError:
            print(f"Error: Could not parse {target_file}")
            return False

        modified = False
        google_lang = LANGUAGE_CODES.get(target_lang, target_lang)

        # Process regular string elements
        for string_elem in root.findall('string'):
            name = string_elem.get('name')
            current_value = string_elem.text or ''

            # Skip if already translated
            if name in target_strings and current_value.strip():
                self.stats['skipped'] += 1
                continue

            # Get source text
            if name not in source_strings:
                print(f"Warning: String '{name}' not found in source")
                continue

            source_text = source_strings[name]
            self.stats['total_processed'] += 1

            # Skip if source is empty
            if not source_text.strip():
                continue

            # Translate
            translated = self.translate_text(source_text, google_lang)
            if translated:
                string_elem.text = translated
                # Add comment marker
                comment = ET.Comment(" MT fallback ")
                string_elem.append(comment)
                self.stats['mt_added'] += 1
                modified = True
                print(f"  Added MT for: {name}")

        # Process plurals
        for plurals_elem in root.findall('plurals'):
            name = plurals_elem.get('name')
            for item in plurals_elem.findall('item'):
                quantity = item.get('quantity')
                key = f"{name}_{quantity}"
                current_value = item.text or ''

                # Skip if already translated
                if key in target_strings and current_value.strip():
                    self.stats['skipped'] += 1
                    continue

                # Get source text
                if key not in source_strings:
                    continue

                source_text = source_strings[key]
                self.stats['total_processed'] += 1

                # Skip if source is empty
                if not source_text.strip():
                    continue

                # Translate
                translated = self.translate_text(source_text, google_lang)
                if translated:
                    item.text = translated
                    # Add comment marker
                    comment = ET.Comment(" MT fallback ")
                    item.append(comment)
                    self.stats['mt_added'] += 1
                    modified = True
                    print(f"  Added MT for plural: {name} ({quantity})")

        # Write modified file
        if modified:
            # Ensure proper XML declaration and encoding
            with open(target_file, 'w', encoding='utf-8') as f:
                f.write('<?xml version="1.0" encoding="utf-8"?>\n')
                tree.write(f, encoding='unicode', xml_declaration=False)

        return modified

    def validate_xml(self, file_path: Path) -> bool:
        """
        Validate that an XML file is well-formed.
        """
        try:
            ET.parse(file_path)
            return True
        except ET.ParseError as e:
            print(f"XML validation failed for {file_path}: {e}")
            return False


def main():
    """Main execution function."""
    print("Starting Machine Translation Fallback Process...")
    print("=" * 60)

    # Get API key from environment
    api_key = os.environ.get('GOOGLE_TRANSLATE_API_KEY')
    if not api_key:
        print("Warning: GOOGLE_TRANSLATE_API_KEY not set")
        print("Skipping machine translation fallback...")
        print("To enable MT fallback, set the GOOGLE_TRANSLATE_API_KEY secret in GitHub")
        return

    # Get languages to process
    languages_str = os.environ.get('LANGUAGES', 'ja,ko,vi')
    languages = [lang.strip() for lang in languages_str.split(',')]
    print(f"Processing languages: {', '.join(languages)}")

    # Initialize handler
    handler = MTFallbackHandler(api_key)

    # Base paths
    project_root = Path(__file__).parent.parent.parent
    values_dir = project_root / 'app' / 'src' / 'main' / 'res' / 'values'

    # Process each language
    total_modified_files = 0
    for lang in languages:
        if lang not in LANGUAGE_CODES:
            print(f"Warning: Unsupported language code '{lang}', skipping...")
            continue

        print(f"\n{'='*60}")
        print(f"Processing language: {lang}")
        print('='*60)

        lang_dir = project_root / 'app' / 'src' / 'main' / 'res' / f'values-{lang}'

        if not lang_dir.exists():
            print(f"Warning: Language directory {lang_dir} does not exist, skipping...")
            continue

        # Process each string file
        for string_file in STRING_FILES:
            source_file = values_dir / string_file
            target_file = lang_dir / string_file

            if not source_file.exists():
                print(f"Warning: Source file {source_file} does not exist, skipping...")
                continue

            if not target_file.exists():
                print(f"Warning: Target file {target_file} does not exist, skipping...")
                continue

            # Validate source XML
            if not handler.validate_xml(source_file):
                print(f"Error: Source file {source_file} is not valid XML")
                continue

            # Add missing translations
            if handler.add_missing_translations(source_file, target_file, lang):
                # Validate modified file
                if handler.validate_xml(target_file):
                    total_modified_files += 1
                    print(f"  ✓ Successfully updated {target_file.name}")
                else:
                    print(f"  ✗ Error: Modified file {target_file} is not valid XML")

    # Print summary
    print("\n" + "=" * 60)
    print("MT Fallback Summary")
    print("=" * 60)
    print(f"Total strings processed: {handler.stats['total_processed']}")
    print(f"Machine translations added: {handler.stats['mt_added']}")
    print(f"Already translated (skipped): {handler.stats['skipped']}")
    print(f"Errors encountered: {handler.stats['errors']}")
    print(f"Files modified: {total_modified_files}")
    print("=" * 60)

    if handler.stats['mt_added'] > 0:
        print("\n✓ Machine translation fallback completed successfully!")
        print(f"  Added {handler.stats['mt_added']} machine-translated strings")
    else:
        print("\n✓ No missing translations found or MT fallback disabled")

    return 0


if __name__ == '__main__':
    sys.exit(main())
