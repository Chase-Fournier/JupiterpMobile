import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // Custom scheme links (jupiterp://...)
                .onOpenURL { url in
                    DeepLinkHandler.shared.onDeepLink(url: url.absoluteString)
                }
                // Universal Links (https://jupiterp.com/?s=...)
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        DeepLinkHandler.shared.onDeepLink(url: url.absoluteString)
                    }
                }
        }
    }
}
