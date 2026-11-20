#!/system/bin/sh
MODDIR=${0%/*}
TARGET_ICONS="/data/oplus/uxicons"

if [ -f "$MODDIR/uxicons.zip" ]; then
  
 rm -rf "$TARGET_ICONS"/*
  
 unzip -o "$MODDIR/uxicons.zip" -d "$TARGET_ICONS" >&2
  
 chmod -R 0777 "$TARGET_ICONS"
  
fi