# Repository Guidelines

## Project Structure & Module Organization
`lib/` contains the public Dart API and platform-specific implementations under `lib/src/native/` and `lib/src/web/`. Native plugin code lives in platform folders such as `android/`, `ios/`, `macos/`, `windows/`, `linux/`, `elinux/`, plus shared code in `common/` and `third_party/`. Tests are in `test/unit/`, and the sample app used for manual verification is under `example/`.

## Build, Test, and Development Commands
Run `flutter pub get` after dependency changes. Use `flutter analyze` to apply the repository lint set from `analysis_options.yaml`. Run `flutter test` for the Dart test suite, or target a file such as `flutter test test/unit/rtc_peerconnection_test.dart`. Use `flutter run -d <device> example/lib/main.dart` to verify plugin behavior in the example app. Format native sources with `./format.sh`; format Dart with `dart format lib test example/lib`.

## Coding Style & Naming Conventions
Follow Flutter and Dart idioms: 2-space indentation, lowerCamelCase for members, UpperCamelCase for types, and snake_case file names such as `rtc_peerconnection_impl.dart`. The repo extends `package:lints/recommended.yaml` and enables additional rules like `always_declare_return_types`, `camel_case_types`, and constructor sorting. Native C/C++/Objective-C files use the checked-in `.clang-format` based on Chromium style.

## Testing Guidelines
Add or update tests in `test/unit/` when changing Dart behavior; web-specific coverage belongs in `test/unit/web/`. Keep test files named `*_test.dart` and use descriptive test names that explain the expected behavior. The `example/test/` app exists, but plugin changes should not rely on the default smoke test alone. Prefer focused unit tests for regressions and run the affected test file locally before opening a PR.

## Commit & Pull Request Guidelines
Recent history follows conventional commit prefixes such as `fix(ios): ...`, `fix(windows): ...`, and `release: ...`. Keep commit subjects imperative and scoped when possible. Before opening a PR, create or link the related issue as suggested in `CONTRIBUTING.md`, summarize platform impact, and include screenshots or logs when UI, device permissions, or runtime behavior changes.

## Platform Notes
This plugin spans Flutter plus native WebRTC bindings. Keep changes narrow to the affected platform, and verify any native edits against the `example/` app on at least one relevant target before requesting review.
