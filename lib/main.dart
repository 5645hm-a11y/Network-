import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:provider/provider.dart';
import 'providers/vpn_provider.dart';
import 'services/vpn_service.dart';
import 'screens/dashboard_screen.dart';
import 'screens/settings_screen.dart';
import 'screens/traffic_screen.dart';
import 'screens/cache_screen.dart';
import 'theme/app_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
  ));
  runApp(const VirtualSimApp());
}

class VirtualSimApp extends StatelessWidget {
  const VirtualSimApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => VpnProvider(VpnService()),
      child: MaterialApp(
        title: 'Virtual SIM',
        theme: appTheme,
        debugShowCheckedModeBanner: false,
        localizationsDelegates: const [
          AppLocalizations.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: const [
          Locale('en'),
          Locale('he'),
        ],
        home: const _AppShell(),
      ),
    );
  }
}

class _AppShell extends StatefulWidget {
  const _AppShell();

  @override
  State<_AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<_AppShell> {
  final _nav = GlobalKey<NavigatorState>();

  @override
  Widget build(BuildContext context) {
    return Navigator(
      key: _nav,
      initialRoute: '/',
      onGenerateRoute: (settings) {
        Widget page;
        switch (settings.name) {
          case '/settings':
            page = SettingsScreen(onBack: () => _nav.currentState!.pop());
            break;
          case '/traffic':
            page = TrafficScreen(onBack: () => _nav.currentState!.pop());
            break;
          case '/cache':
            page = CacheScreen(onBack: () => _nav.currentState!.pop());
            break;
          default:
            page = DashboardScreen(
              onSettings: () => _nav.currentState!.pushNamed('/settings'),
              onTraffic:  () => _nav.currentState!.pushNamed('/traffic'),
              onCache:    () => _nav.currentState!.pushNamed('/cache'),
            );
        }
        return MaterialPageRoute(builder: (_) => page, settings: settings);
      },
    );
  }
}
