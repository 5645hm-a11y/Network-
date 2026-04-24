import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:provider/provider.dart';
import '../models/vpn_state.dart';
import '../providers/vpn_provider.dart';
import '../services/vpn_service.dart';
import '../theme/app_theme.dart';
import '../widgets/ui_kit.dart';

class SettingsScreen extends StatefulWidget {
  final VoidCallback onBack;
  const SettingsScreen({super.key, required this.onBack});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _ctrl = TextEditingController();
  final _svc  = VpnService();
  bool _visible = false;
  bool _saved   = false;

  @override
  void initState() {
    super.initState();
    _svc.getApiKey().then((k) { if (mounted) _ctrl.text = k; });
  }

  @override
  void dispose() { _ctrl.dispose(); super.dispose(); }

  Future<void> _save() async {
    await _svc.setApiKey(_ctrl.text.trim());
    if (mounted) setState(() => _saved = true);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final s = context.watch<VpnProvider>().state;

    return Container(
      decoration: BoxDecoration(gradient: bgGradient),
      child: SafeArea(
        child: Column(
          children: [
            _header(l),
            Expanded(
              child: SingleChildScrollView(
                physics: const BouncingScrollPhysics(),
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    SectionLabel(l.agentPanel, color: cViolet),
                    _aiSection(l, s),
                    const SizedBox(height: 20),
                    SectionLabel(l.security, color: cElectric),
                    _certSection(l, s),
                    const SizedBox(height: 20),
                    SectionLabel(l.cacheSection, color: cMint),
                    _cacheSection(l, s),
                    const SizedBox(height: 20),
                    SectionLabel(l.about, color: cTextSec),
                    _aboutSection(l),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _header(AppLocalizations l) => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
        child: Row(
          children: [
            GestureDetector(
              onTap: widget.onBack,
              behavior: HitTestBehavior.opaque,
              child: const Padding(
                padding: EdgeInsets.all(8),
                child: Icon(Icons.arrow_back_ios_new_rounded, color: cTextPrim, size: 20),
              ),
            ),
            const SizedBox(width: 4),
            Text(l.settings,
                style: const TextStyle(
                    color: cTextPrim, fontSize: 20, fontWeight: FontWeight.w700)),
          ],
        ),
      );

  Widget _aiSection(AppLocalizations l, VpnState s) {
    return GlassPanel(
      glowColor: cViolet,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            const Icon(Icons.psychology_rounded, color: cViolet, size: 20),
            const SizedBox(width: 10),
            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(l.apiKeyLabel,
                  style: const TextStyle(color: cTextPrim, fontWeight: FontWeight.w600, fontSize: 14)),
              const Text('Claude Haiku',
                  style: TextStyle(color: cTextSec, fontSize: 11)),
            ]),
          ]),
          const SizedBox(height: 14),
          _KeyField(
            ctrl: _ctrl,
            visible: _visible,
            onToggle: () => setState(() => _visible = !_visible),
            onChanged: (_) => setState(() => _saved = false),
            hint: l.apiKeyHint,
          ),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(
              child: NeonButton(
                label: _saved ? l.saved : l.saveKey,
                icon: _saved ? Icons.check_rounded : Icons.save_rounded,
                color: cViolet,
                onTap: _save,
                height: 44,
              ),
            ),
            if (_ctrl.text.isNotEmpty) ...[
              const SizedBox(width: 10),
              GestureDetector(
                onTap: () {
                  _ctrl.clear();
                  _svc.setApiKey('');
                  setState(() => _saved = false);
                },
                child: Container(
                  height: 44,
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  decoration: BoxDecoration(
                    color: cSurface,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: cBorder),
                  ),
                  child: const Center(
                    child: Text('✕', style: TextStyle(color: cTextSec, fontSize: 16)),
                  ),
                ),
              ),
            ],
          ]),
          const SizedBox(height: 10),
          Row(children: [
            PulsingDot(color: s.agentHasKey ? cMint : cAmber, size: 7),
            const SizedBox(width: 8),
            Text(
              s.agentHasKey ? l.agentActive : l.agentInactive,
              style: TextStyle(
                  color: s.agentHasKey ? cMint : cAmber, fontSize: 11.5),
            ),
          ]),
        ],
      ),
    );
  }

  Widget _certSection(AppLocalizations l, VpnState s) {
    return GlassPanel(
      child: Row(children: [
        const Icon(Icons.security_rounded, color: cElectric, size: 22),
        const SizedBox(width: 12),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(l.caCert,
              style: const TextStyle(color: cTextPrim, fontWeight: FontWeight.w600, fontSize: 14)),
          Text(s.caCertReady ? l.caCertReady : l.caCertSubtitle,
              style: TextStyle(color: s.caCertReady ? cMint : cTextSec, fontSize: 11.5)),
        ])),
        const SizedBox(width: 12),
        SizedBox(
          width: 88,
          child: NeonButton(
            label: s.caCertReady ? l.installed : l.install,
            color: s.caCertReady ? cMint : cElectric,
            onTap: () => _svc.installCaCert(),
            outlined: s.caCertReady,
            height: 40,
          ),
        ),
      ]),
    );
  }

  Widget _cacheSection(AppLocalizations l, VpnState s) {
    return GlassPanel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            const Icon(Icons.storage_rounded, color: cMint, size: 22),
            const SizedBox(width: 12),
            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(l.cacheStats,
                  style: const TextStyle(color: cTextPrim, fontWeight: FontWeight.w600, fontSize: 14)),
              Text('${s.cacheEntryCount} ${l.entries} · ${s.cacheSizeMb.toStringAsFixed(1)} MB',
                  style: const TextStyle(color: cTextSec, fontSize: 11.5)),
            ]),
          ]),
          const SizedBox(height: 14),
          Row(children: [
            Expanded(
              child: NeonButton(
                label: l.purgeExpired, color: cElectric,
                onTap: () => _svc.purgeExpired(),
                outlined: true, height: 42,
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: NeonButton(
                label: l.clearAll, color: cRed,
                onTap: () => _svc.clearCache(),
                outlined: true, height: 42,
              ),
            ),
          ]),
        ],
      ),
    );
  }

  Widget _aboutSection(AppLocalizations l) {
    return GlassPanel(
      child: Row(children: [
        Container(
          width: 44, height: 44,
          decoration: BoxDecoration(
            color: cViolet.withAlpha(20),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: cViolet.withAlpha(60)),
          ),
          child: const Icon(Icons.sim_card_rounded, color: cViolet, size: 22),
        ),
        const SizedBox(width: 14),
        Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('Virtual SIM',
              style: TextStyle(color: cTextPrim, fontWeight: FontWeight.w700, fontSize: 15)),
          Text(l.version, style: const TextStyle(color: cTextSec, fontSize: 11.5)),
          Text(l.managedByAI, style: const TextStyle(color: cViolet, fontSize: 11.5)),
        ]),
      ]),
    );
  }
}

class _KeyField extends StatelessWidget {
  final TextEditingController ctrl;
  final bool visible;
  final VoidCallback onToggle;
  final ValueChanged<String> onChanged;
  final String hint;

  const _KeyField({
    required this.ctrl,
    required this.visible,
    required this.onToggle,
    required this.onChanged,
    required this.hint,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF080D18),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: cBorder),
      ),
      child: Row(children: [
        Expanded(
          child: TextField(
            controller: ctrl,
            obscureText: !visible,
            onChanged: onChanged,
            style: const TextStyle(color: cTextPrim, fontSize: 13),
            decoration: InputDecoration(
              hintText: hint,
              hintStyle: const TextStyle(color: cTextSec),
              border: InputBorder.none,
              contentPadding:
                  const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
            ),
          ),
        ),
        GestureDetector(
          onTap: onToggle,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Icon(
              visible ? Icons.visibility_off : Icons.visibility,
              color: cTextSec, size: 18,
            ),
          ),
        ),
      ]),
    );
  }
}
