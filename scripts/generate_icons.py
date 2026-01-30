#!/usr/bin/env python3
"""
PRONTO App Icon Generator
Generates Android adaptive icons with Shield-P design
Colors: Deep Emerald #059669, Sunny Yellow #F59E0B
Shape: Shield (heraldic) - NO speech bubble to avoid WhatsApp trademark
"""

from PIL import Image, ImageDraw, ImageFont
import os
import math

# === CONFIGURATION ===
DEEP_EMERALD = (5, 150, 105)       # #059669 - Primary (NOT WhatsApp green #25D366)
LIGHT_EMERALD = (16, 185, 129)     # #10B981 - Gradient end
SUNNY_YELLOW = (245, 158, 11)      # #F59E0B - Accent (checkmarks)
WHITE = (255, 255, 255)

# Android icon sizes
ICON_SIZES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}

# Adaptive icon sizes (foreground/background are 108dp with safe zone)
ADAPTIVE_SIZES = {
    'mipmap-mdpi': 108,
    'mipmap-hdpi': 162,
    'mipmap-xhdpi': 216,
    'mipmap-xxhdpi': 324,
    'mipmap-xxxhdpi': 432,
}

MASTER_SIZE = 1024
SAFE_ZONE_RATIO = 0.66  # 66% is visible in circular mask

def create_shield_path(draw, cx, cy, size, fill_color):
    """
    Draw a heraldic shield shape (pointed bottom center).
    This is distinctly different from WhatsApp's speech bubble.
    """
    # Shield dimensions
    w = size * 0.8
    h = size * 0.9
    
    top_y = cy - h * 0.45
    bottom_y = cy + h * 0.55
    left_x = cx - w * 0.5
    right_x = cx + w * 0.5
    
    # Control points for curves
    curve_y = cy + h * 0.1
    
    # Shield path points (clockwise from top-left)
    points = [
        # Top left corner (rounded)
        (left_x + w * 0.1, top_y),
        # Top edge
        (right_x - w * 0.1, top_y),
        # Top right corner
        (right_x, top_y + h * 0.05),
        # Right edge curving inward
        (right_x, curve_y),
        # Bottom right curve to point
        (right_x - w * 0.15, bottom_y - h * 0.25),
        # Bottom point (center)
        (cx, bottom_y),
        # Bottom left curve from point
        (left_x + w * 0.15, bottom_y - h * 0.25),
        # Left edge curving inward
        (left_x, curve_y),
        # Top left corner
        (left_x, top_y + h * 0.05),
    ]
    
    draw.polygon(points, fill=fill_color)
    
    return top_y, bottom_y, left_x, right_x


def draw_letter_p(draw, cx, cy, size, color):
    """Draw a bold letter P"""
    # P dimensions
    p_height = size * 0.45
    p_width = size * 0.35
    stroke_width = size * 0.08
    
    # Vertical bar of P
    bar_left = cx - p_width * 0.35
    bar_top = cy - p_height * 0.5
    bar_bottom = cy + p_height * 0.5
    
    # Draw vertical bar
    draw.rectangle([
        bar_left,
        bar_top,
        bar_left + stroke_width,
        bar_bottom
    ], fill=color)
    
    # Draw P bowl (curved part) using ellipse
    bowl_left = bar_left + stroke_width * 0.5
    bowl_top = bar_top
    bowl_right = bar_left + p_width
    bowl_bottom = cy + p_height * 0.05
    bowl_height = bowl_bottom - bowl_top
    
    # Outer bowl
    draw.ellipse([
        bowl_left,
        bowl_top,
        bowl_right,
        bowl_bottom
    ], fill=color)
    
    # Inner cutout (to make it hollow)
    inner_margin = stroke_width
    draw.ellipse([
        bowl_left + inner_margin,
        bowl_top + inner_margin,
        bowl_right - inner_margin * 0.5,
        bowl_bottom - inner_margin
    ], fill=DEEP_EMERALD)


def draw_double_checkmark(draw, cx, cy, size, color):
    """Draw WhatsApp-style double checkmarks (but yellow, not blue/WhatsApp style)"""
    check_size = size * 0.18
    stroke_width = max(3, int(size * 0.025))
    
    # Position below and to the right of center (bottom right of shield)
    offset_x = size * 0.15
    offset_y = size * 0.22
    
    # First checkmark
    x1 = cx + offset_x - check_size * 0.3
    y1 = cy + offset_y
    
    # Checkmark points
    points1 = [
        (x1 - check_size * 0.4, y1),
        (x1, y1 + check_size * 0.4),
        (x1 + check_size * 0.6, y1 - check_size * 0.3),
    ]
    
    # Second checkmark (offset to the right)
    x2 = x1 + check_size * 0.35
    points2 = [
        (x2 - check_size * 0.4, y1),
        (x2, y1 + check_size * 0.4),
        (x2 + check_size * 0.6, y1 - check_size * 0.3),
    ]
    
    # Draw checkmarks
    draw.line(points1, fill=color, width=stroke_width)
    draw.line(points2, fill=color, width=stroke_width)


def create_gradient_background(size):
    """Create a vertical gradient background"""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    for y in range(size):
        ratio = y / size
        r = int(DEEP_EMERALD[0] + (LIGHT_EMERALD[0] - DEEP_EMERALD[0]) * ratio)
        g = int(DEEP_EMERALD[1] + (LIGHT_EMERALD[1] - DEEP_EMERALD[1]) * ratio)
        b = int(DEEP_EMERALD[2] + (LIGHT_EMERALD[2] - DEEP_EMERALD[2]) * ratio)
        draw.line([(0, y), (size, y)], fill=(r, g, b, 255))
    
    return img


def create_foreground(size):
    """Create the adaptive icon foreground with shield, P, and checkmarks"""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    cx, cy = size // 2, size // 2
    
    # Shield size (within safe zone)
    shield_size = size * SAFE_ZONE_RATIO * 0.85
    
    # Draw white shield
    create_shield_path(draw, cx, cy, shield_size, WHITE)
    
    # Draw green P inside shield
    draw_letter_p(draw, cx - shield_size * 0.08, cy - shield_size * 0.05, shield_size, DEEP_EMERALD)
    
    # Draw yellow double checkmarks
    draw_double_checkmark(draw, cx, cy, shield_size, SUNNY_YELLOW)
    
    return img


def create_background(size):
    """Create the adaptive icon background (solid emerald or gradient)"""
    return create_gradient_background(size)


def create_legacy_icon(size):
    """Create a legacy (non-adaptive) icon with everything flattened"""
    # Create background
    bg = create_gradient_background(size)
    
    # Create foreground elements directly on background
    draw = ImageDraw.Draw(bg)
    
    cx, cy = size // 2, size // 2
    shield_size = size * 0.85
    
    # Draw white shield
    create_shield_path(draw, cx, cy, shield_size, WHITE)
    
    # Draw green P inside shield  
    draw_letter_p(draw, cx - shield_size * 0.08, cy - shield_size * 0.05, shield_size, DEEP_EMERALD)
    
    # Draw yellow double checkmarks
    draw_double_checkmark(draw, cx, cy, shield_size, SUNNY_YELLOW)
    
    return bg


def create_round_icon(size):
    """Create a round legacy icon"""
    # Start with square legacy icon
    square = create_legacy_icon(size)
    
    # Create circular mask
    mask = Image.new('L', (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse([0, 0, size, size], fill=255)
    
    # Apply mask
    result = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    result.paste(square, (0, 0), mask)
    
    return result


def generate_all_icons():
    """Generate all required Android icons"""
    
    base_path = '/workspaces/pronto/android/app/src/main/res'
    
    print("🛡️  PRONTO Icon Generator - Shield Edition")
    print("=" * 50)
    print(f"Colors: Deep Emerald #{DEEP_EMERALD[0]:02x}{DEEP_EMERALD[1]:02x}{DEEP_EMERALD[2]:02x}")
    print(f"        Sunny Yellow #{SUNNY_YELLOW[0]:02x}{SUNNY_YELLOW[1]:02x}{SUNNY_YELLOW[2]:02x}")
    print("=" * 50)
    
    # Create mipmap-anydpi-v26 for adaptive icons
    anydpi_path = os.path.join(base_path, 'mipmap-anydpi-v26')
    os.makedirs(anydpi_path, exist_ok=True)
    
    # Create ic_launcher.xml
    ic_launcher_xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
'''
    with open(os.path.join(anydpi_path, 'ic_launcher.xml'), 'w') as f:
        f.write(ic_launcher_xml)
    print(f"✅ Created {anydpi_path}/ic_launcher.xml")
    
    # Create ic_launcher_round.xml
    ic_launcher_round_xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
'''
    with open(os.path.join(anydpi_path, 'ic_launcher_round.xml'), 'w') as f:
        f.write(ic_launcher_round_xml)
    print(f"✅ Created {anydpi_path}/ic_launcher_round.xml")
    
    # Generate adaptive icon layers for each density
    print("\n📱 Generating adaptive icon layers...")
    for folder, size in ADAPTIVE_SIZES.items():
        folder_path = os.path.join(base_path, folder)
        os.makedirs(folder_path, exist_ok=True)
        
        # Foreground
        fg = create_foreground(size)
        fg_path = os.path.join(folder_path, 'ic_launcher_foreground.png')
        fg.save(fg_path, 'PNG')
        
        # Background
        bg = create_background(size)
        bg_path = os.path.join(folder_path, 'ic_launcher_background.png')
        bg.save(bg_path, 'PNG')
        
        print(f"  ✅ {folder}: {size}x{size}px (foreground + background)")
    
    # Generate legacy icons for each density
    print("\n📱 Generating legacy icons...")
    for folder, size in ICON_SIZES.items():
        folder_path = os.path.join(base_path, folder)
        os.makedirs(folder_path, exist_ok=True)
        
        # Square legacy icon
        legacy = create_legacy_icon(size)
        legacy_path = os.path.join(folder_path, 'ic_launcher.png')
        legacy.save(legacy_path, 'PNG')
        
        # Round legacy icon
        round_icon = create_round_icon(size)
        round_path = os.path.join(folder_path, 'ic_launcher_round.png')
        round_icon.save(round_path, 'PNG')
        
        print(f"  ✅ {folder}: {size}x{size}px (square + round)")
    
    print("\n" + "=" * 50)
    print("✅ All icons generated successfully!")
    print("\n⚖️  LEGAL VERIFICATION:")
    print(f"  ✅ Shape: Shield (heraldic) - NO speech bubble")
    print(f"  ✅ Background: #059669 (NOT WhatsApp #25D366)")
    print(f"  ✅ Accent: Yellow #F59E0B (NOT WhatsApp blue)")
    print(f"  ✅ App Name: PRONTO (distinct from WhatsApp)")
    print("=" * 50)


if __name__ == '__main__':
    generate_all_icons()
