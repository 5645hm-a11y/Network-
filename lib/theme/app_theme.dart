import 'package:flutter/material.dart';

const bgDeep    = Color(0xFF060914);
const bgCard    = Color(0xFF0C1428);
const bgSurface = Color(0xFF111D35);
const electric  = Color(0xFF00B4FF);
const mint      = Color(0xFF00F5A0);
const violetAI  = Color(0xFF9B72FF);
const cyanSim   = Color(0xFF00E5FF);
const amber     = Color(0xFFFFB800);
const redStop   = Color(0xFFFF3B30);
const textPrim  = Color(0xFFE8F4FD);
const textSec   = Color(0xFF7B9AB2);

final appTheme = ThemeData(
  brightness: Brightness.dark,
  scaffoldBackgroundColor: bgDeep,
  colorScheme: const ColorScheme.dark(
    surface: bgCard,
    primary: electric,
    secondary: mint,
    tertiary: violetAI,
    error: redStop,
    onSurface: textPrim,
    onPrimary: bgDeep,
  ),
  textTheme: const TextTheme(
    displayLarge: TextStyle(color: textPrim, fontWeight: FontWeight.bold),
    headlineMedium: TextStyle(color: textPrim, fontWeight: FontWeight.bold),
    titleLarge: TextStyle(color: textPrim, fontWeight: FontWeight.w600),
    titleMedium: TextStyle(color: textPrim, fontWeight: FontWeight.w500),
    bodyLarge: TextStyle(color: textPrim),
    bodyMedium: TextStyle(color: textSec),
    bodySmall: TextStyle(color: textSec),
    labelSmall: TextStyle(color: textSec, letterSpacing: 1.2),
  ),
  cardTheme: CardThemeData(
    color: bgCard,
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    elevation: 0,
  ),
  inputDecorationTheme: InputDecorationTheme(
    filled: true,
    fillColor: const Color(0xFF0A0F20),
    border: OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: const BorderSide(color: Color(0xFF2A3A55)),
    ),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: const BorderSide(color: Color(0xFF2A3A55)),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: const BorderSide(color: violetAI, width: 2),
    ),
    hintStyle: const TextStyle(color: textSec),
    labelStyle: const TextStyle(color: textSec),
  ),
  elevatedButtonTheme: ElevatedButtonThemeData(
    style: ElevatedButton.styleFrom(
      backgroundColor: electric,
      foregroundColor: bgDeep,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
    ),
  ),
  outlinedButtonTheme: OutlinedButtonThemeData(
    style: OutlinedButton.styleFrom(
      foregroundColor: electric,
      side: const BorderSide(color: electric),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
    ),
  ),
  iconTheme: const IconThemeData(color: textSec),
  dividerColor: Color(0xFF1A2A40),
);
