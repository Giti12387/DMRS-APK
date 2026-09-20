import base64
import os

# Read the HTML file
with open(r'E:\whisk\Prepora_icon.html', 'r') as f:
    content = f.read()

# Extract base64 data
start = content.find('base64,') + 7
end = content.find('"', start)
b64_data = content[start:end]

# Decode and save as PNG
png_data = base64.b64decode(b64_data)
output_path = r'D:\ZoroApps\Prepora_icon.png'
with open(output_path, 'wb') as f:
    f.write(png_data)

print(f'PNG saved to {output_path}')
print(f'Size: {len(png_data)} bytes')
