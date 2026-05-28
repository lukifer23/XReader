package com.xreader.app.readium

import android.content.Context
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class ReadiumRuntime(context: Context) {
    private val appContext = context.applicationContext
    val httpClient = DefaultHttpClient()
    val assetRetriever = AssetRetriever(appContext.contentResolver, httpClient)
    val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            appContext,
            assetRetriever = assetRetriever,
            httpClient = httpClient,
            pdfFactory = PdfiumDocumentFactory(appContext)
        ),
        contentProtections = emptyList()
    )
}
