import 'package:flutter/material.dart';

// ── Palette ──────────────────────────────────────────────────────────────────
const Color cBg       = Color(0xFF04070F);   // near-black background
const Color cSurface  = Color(0xFF0A1020);   // card surface
const Color cBorder   = Color(0xFF1A2A45);   // subtle border
const Color cElectric = Color(0xFF00C8FF);   // electric blue
const Color cMint     = Color(0xFF00F5A0);   // mint green
const Color cViolet   = Color(0xFF9B72FF);   // violet (AI)
const Color cCyan     = Color(0xFF00E5FF);   // cyan (serve)
const Color cAmber    = Color(0xFFFFB800);   // amber (warning)
const Color cRed      = Color(0xFFFF3B30);   // red (stop/error)
const Color cTextPrim = Color(0xFFE6F0FF);   // primary text
const Color cTextSec  = Color(0xFF4A6480);   // secondary text
const Color cTextMid  = Color(0xFF8AAABF);   // mid text

// Pre-built gradients
final bgGradient = const LinearGradient(
  begin: Alignment.topLeft,
  end: Alignment.bottomRight,
  colors: [Color(0xFF06091A), Color(0xFF040810), Color(0xFF020508)],
  stops: [0, 0.5, 1],
);

LinearGradient glowGradient(Color c) => LinearGradient(
  colors: [c.withAlpha(40), c.withAlpha(10)],
  begin: Alignment.topLeft,
  end: Alignment.bottomRight,
);

// ── Theme ─────────────────────────────────────────────────────────────────────
final appTheme = ThemeData(
  useMaterial3: false,
  brightness: Brightness.dark,
  scaffoldBackgroundColor: cBg,
  colorScheme: const ColorScheme.dark(
    surface: cSurface,
    primary: cElectric,
    secondary: cMint,
    tertiary: cViolet,
    error: cRed,
  ),
  textTheme: const TextTheme(
    displayLarge : TextStyle(color: cTextPrim, fontWeight: FontWeight.w700, letterSpacing: -1),
    headlineMedium: TextStyle(color: cTextPrim, fontWeight: FontWeight.w700),
    titleLarge   : TextStyle(color: cTextPrim, fontWeight: FontWeight.w600),
    titleMedium  : TextStyle(color: cTextPrim, fontWeight: FontWeight.w500),
    bodyLarge    : TextStyle(color: cTextPrim),
    bodyMedium   : TextStyle(color: cTextMid),
    bodySmall    : TextStyle(color: cTextSec),
    labelSmall   : TextStyle(color: cTextSec, letterSpacing: 1.4, fontWeight: FontWeight.w600),
  ),
);
