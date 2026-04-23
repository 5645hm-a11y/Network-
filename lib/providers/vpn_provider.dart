import 'dart:async';
import 'package:flutter/foundation.dart';
import '../models/vpn_state.dart';
import '../services/vpn_service.dart';

class VpnProvider extends ChangeNotifier {
  final VpnService _svc;

  VpnProvider(this._svc) {
    _stateSubscription = _svc.stateStream.listen(
      (s) { _state = s; notifyListeners(); },
      onError: (_) {},
    );
    _trafficSubscription = _svc.trafficStream.listen(
      (e) { _traffic.insert(0, e); if (_traffic.length > 200) _traffic.removeLast(); notifyListeners(); },
      onError: (_) {},
    );
  }

  VpnState _state = const VpnState();
  final List<TrafficEvent> _traffic = [];
  StreamSubscription<VpnState>? _stateSubscription;
  StreamSubscription<TrafficEvent>? _trafficSubscription;
  String? _error;

  VpnState get state => _state;
  List<TrafficEvent> get traffic => List.unmodifiable(_traffic);
  String? get error => _error;

  Future<void> startAbsorb(int quotaMb) async {
    _error = null;
    try {
      await _svc.startAbsorb(quotaMb);
    } catch (e) {
      _error = e.toString();
      notifyListeners();
    }
  }

  Future<void> startServe() async {
    _error = null;
    try {
      await _svc.startServe();
    } catch (e) {
      _error = e.toString();
      notifyListeners();
    }
  }

  Future<void> stop() async {
    try { await _svc.stop(); } catch (_) {}
  }

  @override
  void dispose() {
    _stateSubscription?.cancel();
    _trafficSubscription?.cancel();
    super.dispose();
  }
}
