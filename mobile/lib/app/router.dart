import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/auth/auth_controller.dart';
import '../core/auth/auth_providers.dart';
import '../core/widgets/app_scaffold.dart';
import '../features/auth/presentation/login_page.dart';
import '../features/customers/presentation/customers_page.dart';
import '../features/home/presentation/home_page.dart';
import '../features/inventory/presentation/inventory_page.dart';
import '../features/more/presentation/more_page.dart';
import '../features/orders/presentation/order_detail_page.dart';
import '../features/orders/presentation/order_edit_page.dart';
import '../features/orders/presentation/orders_page.dart';
import '../features/payments/presentation/payment_page.dart';
import '../features/products/presentation/products_page.dart';

final routerProvider = Provider<GoRouter>((ref) {
  final auth = ref.read(authControllerProvider);
  return GoRouter(
    initialLocation: '/',
    refreshListenable: auth,
    redirect: (context, state) {
      final status = auth.state.status;
      final location = state.matchedLocation;
      final public = location == '/' || location == '/login';
      if (status == AuthStatus.unknown) {
        return location == '/' ? null : '/';
      }
      if (status == AuthStatus.unauthenticated) {
        return public ? null : '/login';
      }
      if (status == AuthStatus.authenticated && public) {
        return '/home';
      }
      return null;
    },
    routes: [
      GoRoute(path: '/', builder: (context, state) => const SplashPage()),
      GoRoute(path: '/login', builder: (context, state) => const LoginPage()),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) {
          return ShellScaffold(
            currentIndex: navigationShell.currentIndex,
            onSelect: navigationShell.goBranch,
            child: navigationShell,
          );
        },
        branches: [
          StatefulShellBranch(
            routes: [GoRoute(path: '/home', builder: (context, state) => const HomePage())],
          ),
          StatefulShellBranch(
            routes: [GoRoute(path: '/orders', builder: (context, state) => const OrdersPage())],
          ),
          StatefulShellBranch(
            routes: [GoRoute(path: '/customers', builder: (context, state) => const CustomersPage())],
          ),
          StatefulShellBranch(
            routes: [GoRoute(path: '/more', builder: (context, state) => const MorePage())],
          ),
        ],
      ),
      GoRoute(
        path: '/orders/new',
        builder: (context, state) {
          final extra = state.extra;
          final seed = extra is OrderEditSeed ? extra : null;
          return OrderEditPage(
            customerId: state.uri.queryParameters['customerId'] ?? seed?.customerId,
            customerName: state.uri.queryParameters['customerName'] ?? seed?.customerName,
            seed: seed,
          );
        },
      ),
      GoRoute(
        path: '/orders/collect',
        builder: (context, state) => const Scaffold(body: OrdersPage(collectMode: true)),
      ),
      GoRoute(
        path: '/orders/:orderId',
        builder: (context, state) => OrderDetailPage(orderId: state.pathParameters['orderId']!),
      ),
      GoRoute(
        path: '/orders/:orderId/edit',
        builder: (context, state) => OrderEditPage(orderId: state.pathParameters['orderId']),
      ),
      GoRoute(
        path: '/orders/:orderId/payment',
        builder: (context, state) => PaymentPage(orderId: state.pathParameters['orderId']!),
      ),
      GoRoute(
        path: '/customers/:customerId',
        builder: (context, state) => CustomerDetailPage(customerId: state.pathParameters['customerId']!),
      ),
      GoRoute(path: '/inventory', builder: (context, state) => const InventoryPage()),
      GoRoute(path: '/products', builder: (context, state) => const ProductsPage()),
    ],
  );
});
