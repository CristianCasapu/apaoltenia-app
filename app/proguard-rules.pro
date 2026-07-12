# Pastreaza metodele expuse catre JavaScript (AndroidBridge).
-keepclassmembers class ro.apaoltenia.client.WebAppInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# WorkManager instantiaza worker-ul prin reflectie.
-keep class ro.apaoltenia.client.InvoiceCheckWorker { *; }
