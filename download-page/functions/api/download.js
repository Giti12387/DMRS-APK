const APK_URL = 'https://archive.org/download/apk-store-v1.0.0-latest/APK-Store_v1.0.0%20latest.apk';

export async function onRequestGet(context) {
  return new Response(null, {
    status: 302,
    headers: {
      'Location': APK_URL,
      'Cache-Control': 'no-cache, no-store, must-revalidate'
    }
  });
}
