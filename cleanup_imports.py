#!/usr/bin/env python3

# Read the MainActivity.kt file
with open('/home/engine/project/app/src/main/kotlin/moe/koiverse/archivetune/MainActivity.kt', 'r') as f:
    lines = f.readlines()

# Remove duplicate imports
seen_imports = set()
cleaned_lines = []

for line in lines:
    if line.strip().startswith('import moe.koiverse.archivetune.constants.DynamicColor'):
        import_key = line.strip().split()[-1]  # Get the last part (the import target)
        if import_key not in seen_imports:
            seen_imports.add(import_key)
            cleaned_lines.append(line)
    else:
        cleaned_lines.append(line)

# Write the cleaned content back
with open('/home/engine/project/app/src/main/kotlin/moe/koiverse/archivetune/MainActivity.kt', 'w') as f:
    f.writelines(cleaned_lines)

print("Duplicate imports removed successfully!")