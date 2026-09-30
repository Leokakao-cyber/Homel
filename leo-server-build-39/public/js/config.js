export const siteConfig = {
  brand: { name: "KAKAO", statement: "We build things worth interacting with.", copyright: `© ${new Date().getFullYear()} KAKAO` },
  navigation: [
    { label: "Products", href: "/products" }, { label: "Experiments", href: "/experiments" }, { label: "About", href: "/about" }
  ],
  products: [{
    name: "Lumen", slug: "lumen", eyebrow: "Chapter 01 · Android", statement: "Your words. Smarter.",
    description: "A private Android keyboard that understands, rewrites, translates and remembers—on your terms.",
    version: "1.2.1-alpha", platform: "Android 8.0+", downloadURL: "/downloads/Lumen-Keyboard-1.2.1-alpha-debug.apk",
    fileSize: "858 KB", releaseDate: "September 21, 2026", status: "Alpha test build",
    checksum: "SHA-256 616C08F54D8361F5D119042D313F150D45A328B5F78CD4750612BDF678AF96FC",
    releaseNotes: ["In-app update bell now checks published release notes and marks new updates as unread", "Private local suggestions, corrections and learned vocabulary", "Dictionary, Grammar, Rewrite, Translate, Clipboard, Emoji and Voice tools", "Double-tap Caps Lock, newline Enter key, expanded symbols and currency layouts", "Hold Space to open LEO, bordered keys, themes, resizing and 20 key-font styles", "Signed debug APK for direct testing; Play Store release signing and physical-device QA are still pending"]
  }]
};
export const getProduct = (slug) => siteConfig.products.find((product) => product.slug === slug);
