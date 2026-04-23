import 'package:flutter/services.dart';
import '../models/vpn_state.dart';

class VpnService {
  static const _control = MethodChannel('com.networkabsorb/vpn_control');
  static const _settingsCh = MethodChannel('com.networkabsorb/settings');
  static const _stateEvent = EventChannel('com.networkabsorb/vpn_state');
  static const _trafficEvent = EventChannel('com.networkabsorb/traffic_feed');

  Stream<VpnState> get stateStream =>
      _stateEvent.receiveBroadcastStream().map(
        (e) => VpnState.fromMap(e as Map),
      );

  Stream<TrafficEvent> get trafficStream =>
      _trafficEvent.receiveBroadcastStream().map(
        (e) => TrafficEvent.fromMap(e as Map),
      );

  Future<void> startAbsorb(int quotaMb) async {
    await _control.invokeMethod('startAbsorb', {'quotaMb': quotaMb});
  }

  Future<void> startServe() async {
    await _control.invokeMethod('startServe');
  }

  Future<void> stop() async {
    await _control.invokeMethod('stop');
  }

  Future<void> setApiKey(String key) async {
    await _settingsCh.invokeMethod('setApiKey', {'key': key});
  }

  Future<String> getApiKey() async {
    final result = await _settingsCh.invokeMethod<String>('getApiKey');
    return result ?? '';
  }

  Future<Map<String, dynamic>> getCacheStats() async {
    final result = await _settingsCh.invokeMethod<Map>('getCacheStats');
    return Map<String, dynamic>.from(result ?? {});
  }

  Future<void> clearCache() async {
    await _settingsCh.invokeMethod('clearCache');
  }

  Future<void> purgeExpired() async {
    await _settingsCh.invokeMethod('purgeExpired');
  }

  Future<void> installCaCert() async {
    await _settingsCh.invokeMethod('installCaCert');
  }

  Future<List<CacheEntry>> getCacheEntries() async {
    final result =
        await _settingsCh.invokeMethod<List>('getCacheEntries');
    if (result == null) return [];
    return result.map((e) => CacheEntry.fromMap(e as Map)).toList();
  }
}
