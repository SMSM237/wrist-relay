# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Galaxy phone and Galaxy Watch users who want a selected phone notification, initially a Samsung Wallet digital-key door event, to alert on the watch without adding a Wear OS companion app.

## Product Purpose

Wrist Relay lets the user deliberately capture a new notification during a bounded one-hour session, turn the selected event into an editable local matching rule, and relay future matches as watch-bridgeable Android notifications. Success means the intended watch alert is delivered once while the paired phone remains quiet under the documented Galaxy Wearable setting.

## Positioning

The product learns only user-selected, newly occurring notifications and keeps matching, storage, encryption, retention, and diagnostics entirely on the phone.

## Operating Context

The primary validation pair is a Galaxy S25 Edge and Galaxy Watch Ultra (2025). The user grants Android notification access, configures Galaxy Wearable to show Wrist Relay alerts and mute the paired phone, triggers a real source notification, reviews the generated conditions, selects a preset, and manually confirms the watch test.

## Capabilities and Constraints

- Native Kotlin and Jetpack Compose phone application; no Wear OS companion.
- NotificationListenerService capture begins only after an explicit button press and lasts at most one hour.
- Ongoing, grouped, self-generated, and repeated-key updates are excluded.
- Package is mandatory; channel, normalized title phrase, and normalized body phrase are editable conditions.
- Captured content and rule phrases use Android Keystore-backed AES-GCM encryption.
- Cancelled sessions are deleted immediately; the selected setup record expires 30 minutes after rule save.
- No INTERNET, location, accessibility, contacts, SMS, call-log, Bluetooth, NFC, analytics, advertising, or account capability.
- Watch delivery is Android notification mirroring. Physical vibration and Galaxy Wearable-only settings require explicit user confirmation.
- The current installation artifact is a debug-signed APK for direct testing; source and documentation are intended for a public GitHub repository. A store-ready release requires a separate release signing decision and device validation.

## Brand Commitments

The confirmed name is Wrist Relay. Primary copy is Korean. Product statements distinguish observable Android state from manual watch-test confirmation and never claim an automatically verified Galaxy Watch connection.

## Evidence on Hand

- Current product and interface contracts: `README.md`, `DESIGN.md`, `PRIVACY.md`, and `SECURITY.md`.
- Automated unit and Robolectric coverage for policy, domain matching, encrypted retention, listener filtering, and relay-channel behavior.
- No physical S25 Edge or Watch Ultra evidence is available in this remote workspace yet; such combinations must remain labeled unverified.

## Product Principles

- Capture only after explicit user intent and retain the minimum necessary data.
- Fail closed when permissions, encryption, storage, or matching state is uncertain.
- Explain recoverable settings problems in plain Korean at the point of action.
- Prefer capability inspection and user-confirmed tests over brittle device-code branches.
- Never turn a phone-only implementation limitation into a stronger connection claim.

## Accessibility & Inclusion

All primary actions use at least 48 dp touch targets, high-contrast text, clear non-color status labels, screen-reader descriptions, and layouts without horizontal scrolling. The interface uses Wanted Sans when the renderer can resolve the verified static font files.
