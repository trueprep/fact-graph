#!/usr/bin/env python3
"""
Script to download XML files from GitHub URLs listed in fact_dictionaries/links.txt
"""

import requests
import re
from pathlib import Path
from urllib.parse import urlparse


def convert_blob_to_raw_url(blob_url):
    """Convert GitHub blob URL to raw content URL."""
    return blob_url.replace('/blob/', '/raw/')


def extract_filename(url):
    """Extract filename from URL."""
    parsed = urlparse(url)
    path = parsed.path
    # Remove the #L4 fragment if present
    path = path.split('#')[0]
    # Get the filename from the path
    filename = Path(path).name
    return filename


def download_xml_files():
    """Download all XML files from links.txt."""
    # Paths
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    links_file = project_root / 'fact_dictionaries' / 'links.txt'
    output_dir = project_root / 'fact_dictionaries'

    # Ensure output directory exists
    output_dir.mkdir(parents=True, exist_ok=True)

    # Read links from file
    print(f"Reading links from {links_file}...")
    with open(links_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    # Parse URLs (remove "- " prefix and "#L4" suffix)
    urls = []
    for line in lines:
        line = line.strip()
        if line.startswith('- '):
            url = line[2:]  # Remove "- " prefix
            # Remove fragment like "#L4"
            url = url.split('#')[0]
            if url:
                urls.append(url)

    print(f"Found {len(urls)} URLs to download\n")

    # Download each file
    success_count = 0
    fail_count = 0

    for i, url in enumerate(urls, 1):
        try:
            # Convert blob URL to raw URL
            raw_url = convert_blob_to_raw_url(url)
            filename = extract_filename(raw_url)
            output_path = output_dir / filename

            print(f"[{i}/{len(urls)}] Downloading {filename}...")

            # Download the file
            response = requests.get(raw_url, timeout=30)
            response.raise_for_status()

            # Save to file
            with open(output_path, 'wb') as f:
                f.write(response.content)

            print(f"  [OK] Saved to {output_path}")
            success_count += 1

        except requests.exceptions.RequestException as e:
            print(f"  [FAIL] Failed to download {url}: {e}")
            fail_count += 1
        except Exception as e:
            print(f"  [FAIL] Error processing {url}: {e}")
            fail_count += 1

    # Summary
    print(f"\n{'='*60}")
    print(f"Download complete!")
    print(f"  Success: {success_count}")
    print(f"  Failed:  {fail_count}")
    print(f"{'='*60}")


if __name__ == '__main__':
    download_xml_files()
