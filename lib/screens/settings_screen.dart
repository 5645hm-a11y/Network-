import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:provider/provider.dart';
import '../models/vpn_state.dart';
import '../providers/vpn_provider.dart';
import '../services/vpn_service.dart';
import '../theme/app_theme.dart';

class SettingsScreen extends StatefulWidget {
  final VoidCallback onBack;
  const SettingsScreen({super.key, required this.onBack});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _apiKeyController = TextEditingController();
  bool _keyVisible = false;
  bool _keySaved = false;
  final _svc = VpnService();

  @override
  void initState() {
    super.initState();
    _loadKey();
  }

  Future<void> _loadKey() async {
    final key = await _svc.getApiKey();
    if (mounted) _apiKeyController.text = key;
  }

  Future<void> _saveKey() async {
    await _svc.setApiKey(_apiKeyController.text.trim());
    if (mounted) setState(() => _keySaved = true);
  }

  @override
  void dispose() {
    _apiKeyController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final s = context.watch<VpnProvider>().state;

    return Scaffold(
      backgroundColor: bgDeep,
      body: SafeArea(
        child: Column(
          children: [
            _topBar(l),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const SizedBox(height: 8),
                    _sectionLabel(l.agentPanel, violetAI),
                    _aiCard(l, s),
                    const SizedBox(height: 16),
                    _sectionLabel(l.security, electric),
                    _certCard(l, s),
                    const SizedBox(height: 16),
                    _sectionLabel(l.cacheSection, mint),
                    _cacheCard(l, s),
                    const SizedBox(height: 16),
                    _sectionLabel(l.about, textSec),
                    _aboutCard(l),
                    const SizedBox(height: 32),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _topBar(AppLocalizations l) => Row(
        children: [
          IconButton(
            icon: const Icon(Icons.arrow_back_ios_new_rounded, color: textPrim),
            onPressed: widget.onBack,
          ),
          Text(l.settings,
              style: const TextStyle(
                  color: textPrim, fontSize: 20, fontWeight: FontWeight.bold)),
        ],
      );

  Widget _sectionLabel(String text, Color color) => Padding(
        padding: const EdgeInsets.only(bottom: 8, left: 2),
        child: Text(
          text.toUpperCase(),
          style: TextStyle(
              color: color, fontSize: 11, fontWeight: FontWeight.bold, letterSpacing: 1.5),
        ),
      );

  Widget _aiCard(AppLocalizations l, VpnState s) {
    return _DarkCard(
      borderColor: violetAI.withAlpha(60),
      gradient: [const Color(0xFF1A1040), bgCard],
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.psychology_rounded, color: violetAI, size: 22),
              const SizedBox(width: 10),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(l.apiKeyLabel,
                      style: const TextStyle(
                          color: textPrim, fontWeight: FontWeight.w600, fontSize: 15)),
                  Text('Claude Haiku AI',
                      style: const TextStyle(color: textSec, fontSize: 12)),
                ],
              ),
            ],
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _apiKeyController,
            obscureText: !_keyVisible,
            style: const TextStyle(color: textPrim, fontSize: 13),
            onChanged: (_) => setState(() => _keySaved = false),
            decoration: InputDecoration(
              hintText: l.apiKeyHint,
              suffixIcon: IconButton(
                icon: Icon(
                    _keyVisible ? Icons.visibility_off : Icons.visibility,
                    color: textSec, size: 20),
                onPressed: () => setState(() => _keyVisible = !_keyVisible),
              ),
            ),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: violetAI,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                  onPressed: _saveKey,
                  icon: Icon(_keySaved ? Icons.check : Icons.save_rounded, size: 16),
                  label: Text(_keySaved ? l.saved : l.saveKey),
                ),
              ),
              if (_apiKeyController.text.isNotEmpty) ...[
                const SizedBox(width: 8),
                TextButton(
                  onPressed: () {
                    _apiKeyController.clear();
                    _svc.setApiKey('');
                    setState(() => _keySaved = false);
                  },
                  child: Text(l.delete, style: const TextStyle(color: textSec)),
                ),
              ],
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Container(
                width: 8, height: 8,
                decoration: BoxDecoration(
                  color: s.agentHasKey ? mint : amber,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 6),
              Text(
                s.agentHasKey ? l.agentActive : l.agentInactive,
                style: TextStyle(
                    color: s.agentHasKey ? mint : amber, fontSize: 12),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _certCard(AppLocalizations l, VpnState s) {
    return _DarkCard(
      child: Row(
        children: [
          Icon(Icons.security_rounded, color: electric, size: 24),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(l.caCert,
                    style: const TextStyle(
                        color: textPrim, fontWeight: FontWeight.w600, fontSize: 15)),
                Text(s.caCertReady ? l.caCertReady : l.caCertSubtitle,
                    style: TextStyle(
                        color: s.caCertReady ? mint : textSec, fontSize: 12)),
              ],
            ),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: s.caCertReady ? bgSurface : electric,
              foregroundColor: s.caCertReady ? mint : bgDeep,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            ),
            onPressed: () => _svc.installCaCert(),
            child: Text(s.caCertReady ? l.installed : l.install,
                style: const TextStyle(fontSize: 13)),
          ),
        ],
      ),
    );
  }

  Widget _cacheCard(AppLocalizations l, VpnState s) {
    return _DarkCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.storage_rounded, color: mint, size: 24),
              const SizedBox(width: 12),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(l.cacheStats,
                      style: const TextStyle(
                          color: textPrim, fontWeight: FontWeight.w600, fontSize: 15)),
                  Text(
                      '${s.cacheEntryCount} ${l.entries} · ${s.cacheSizeMb.toStringAsFixed(1)} MB',
                      style: const TextStyle(color: textSec, fontSize: 12)),
                ],
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  style: OutlinedButton.styleFrom(
                    foregroundColor: electric,
                    side: BorderSide(color: electric.withAlpha(120)),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    padding: const EdgeInsets.symmetric(vertical: 10),
                  ),
                  onPressed: () => _svc.purgeExpired(),
                  child: Text(l.purgeExpired, style: const TextStyle(fontSize: 13)),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF3D1515),
                    foregroundColor: const Color(0xFFFF6B6B),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    padding: const EdgeInsets.symmetric(vertical: 10),
                  ),
                  onPressed: () => _svc.clearCache(),
                  child: Text(l.clearAll, style: const TextStyle(fontSize: 13)),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _aboutCard(AppLocalizations l) {
    return _DarkCard(
      child: Row(
        children: [
          const Icon(Icons.info_outline_rounded, color: textSec, size: 24),
          const SizedBox(width: 12),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Virtual SIM',
                  style: const TextStyle(
                      color: textPrim, fontWeight: FontWeight.bold, fontSize: 16)),
              Text(l.version,
                  style: const TextStyle(color: textSec, fontSize: 12)),
              Text(l.description,
                  style: const TextStyle(color: textSec, fontSize: 12)),
              Text(l.managedByAI,
                  style: const TextStyle(color: violetAI, fontSize: 12)),
            ],
          ),
        ],
      ),
    );
  }
}

class _DarkCard extends StatelessWidget {
  final Widget child;
  final Color? borderColor;
  final List<Color>? gradient;

  const _DarkCard({required this.child, this.borderColor, this.gradient});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: 0),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: gradient == null ? bgCard : null,
        gradient: gradient != null
            ? LinearGradient(
                colors: gradient!,
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              )
            : null,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: borderColor ?? const Color(0xFF1A2A40)),
      ),
      child: child,
    );
  }
}
