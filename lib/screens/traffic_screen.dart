import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:provider/provider.dart';
import '../models/vpn_state.dart';
import '../providers/vpn_provider.dart';
import '../theme/app_theme.dart';
import '../widgets/ui_kit.dart';

class TrafficScreen extends StatelessWidget {
  final VoidCallback onBack;
  const TrafficScreen({super.key, required this.onBack});

  @override
  Widget build(BuildContext context) {
    final l       = AppLocalizations.of(context)!;
    final traffic = context.watch<VpnProvider>().traffic;

    return Container(
      decoration: BoxDecoration(gradient: bgGradient),
      child: SafeArea(
        child: Column(
          children: [
            _Header(title: l.trafficMonitor, onBack: onBack),
            Expanded(
              child: traffic.isEmpty
                  ? Center(child: Text(l.noTraffic,
                      style: const TextStyle(color: cTextSec, fontSize: 14)))
                  : ListView.builder(
                      physics: const BouncingScrollPhysics(),
                      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                      itemCount: traffic.length,
                      itemBuilder: (_, i) => _TrafficRow(event: traffic[i]),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TrafficRow extends StatelessWidget {
  final TrafficEvent event;
  const _TrafficRow({required this.event});

  @override
  Widget build(BuildContext context) {
    final isHit  = event.type == 'hit';
    final isMiss = event.type == 'miss';
    final color  = isHit ? cMint : isMiss ? cAmber : cElectric;

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 3),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
      decoration: BoxDecoration(
        color: cSurface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: cBorder),
      ),
      child: Row(children: [
        Container(
          width: 6, height: 6, margin: const EdgeInsets.only(right: 10),
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        Expanded(
          child: Text(
            Uri.tryParse(event.url)?.host ?? event.url,
            style: const TextStyle(color: cTextPrim, fontSize: 11.5),
            overflow: TextOverflow.ellipsis,
          ),
        ),
        if (event.statusCode != null) ...[
          const SizedBox(width: 6),
          Text('${event.statusCode}',
              style: TextStyle(
                  color: (event.statusCode ?? 0) < 400 ? cMint : cRed,
                  fontSize: 10.5)),
        ],
        if (event.sizeBytes != null) ...[
          const SizedBox(width: 6),
          Text(_fmtB(event.sizeBytes!),
              style: const TextStyle(color: cTextSec, fontSize: 10.5)),
        ],
      ]),
    );
  }

  String _fmtB(int b) {
    if (b >= 1048576) return '${(b / 1048576).toStringAsFixed(1)}M';
    if (b >= 1024)    return '${(b / 1024).toStringAsFixed(0)}K';
    return '${b}B';
  }
}

class _Header extends StatelessWidget {
  final String title;
  final VoidCallback onBack;
  const _Header({required this.title, required this.onBack});

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 10),
        child: Row(children: [
          GestureDetector(
            onTap: onBack,
            behavior: HitTestBehavior.opaque,
            child: const Padding(
              padding: EdgeInsets.all(8),
              child: Icon(Icons.arrow_back_ios_new_rounded,
                  color: cTextPrim, size: 20),
            ),
          ),
          const SizedBox(width: 4),
          Text(title,
              style: const TextStyle(
                  color: cTextPrim, fontSize: 19, fontWeight: FontWeight.w700)),
        ]),
      );
}
