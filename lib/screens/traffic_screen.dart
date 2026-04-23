import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:provider/provider.dart';
import '../models/vpn_state.dart';
import '../providers/vpn_provider.dart';
import '../theme/app_theme.dart';

class TrafficScreen extends StatelessWidget {
  final VoidCallback onBack;
  const TrafficScreen({super.key, required this.onBack});

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final traffic = context.watch<VpnProvider>().traffic;

    return Scaffold(
      backgroundColor: bgDeep,
      body: SafeArea(
        child: Column(
          children: [
            Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.arrow_back_ios_new_rounded, color: textPrim),
                  onPressed: onBack,
                ),
                Text(l.trafficMonitor,
                    style: const TextStyle(
                        color: textPrim, fontSize: 20, fontWeight: FontWeight.bold)),
              ],
            ),
            Expanded(
              child: traffic.isEmpty
                  ? Center(child: Text(l.noTraffic,
                      style: const TextStyle(color: textSec)))
                  : ListView.builder(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      itemCount: traffic.length,
                      itemBuilder: (_, i) => _TrafficTile(event: traffic[i]),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TrafficTile extends StatelessWidget {
  final TrafficEvent event;
  const _TrafficTile({required this.event});

  @override
  Widget build(BuildContext context) {
    final isHit = event.type == 'hit';
    final isMiss = event.type == 'miss';
    final color = isHit ? mint : isMiss ? amber : electric;
    final icon = isHit ? Icons.offline_bolt_rounded
        : isMiss ? Icons.cloud_off_rounded
        : Icons.download_rounded;

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 3),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: bgCard,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: const Color(0xFF1A2A40)),
      ),
      child: Row(
        children: [
          Icon(icon, color: color, size: 16),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              Uri.tryParse(event.url)?.host ?? event.url,
              style: const TextStyle(color: textPrim, fontSize: 12),
              overflow: TextOverflow.ellipsis,
            ),
          ),
          if (event.statusCode != null)
            Text('${event.statusCode}',
                style: TextStyle(
                    color: (event.statusCode ?? 0) < 400 ? mint : redStop,
                    fontSize: 11)),
          const SizedBox(width: 6),
          if (event.sizeBytes != null)
            Text(_fmtSize(event.sizeBytes!),
                style: const TextStyle(color: textSec, fontSize: 11)),
        ],
      ),
    );
  }

  String _fmtSize(int bytes) {
    if (bytes >= 1024 * 1024) return '${(bytes / 1024 / 1024).toStringAsFixed(1)}MB';
    if (bytes >= 1024) return '${(bytes / 1024).toStringAsFixed(0)}KB';
    return '${bytes}B';
  }
}
