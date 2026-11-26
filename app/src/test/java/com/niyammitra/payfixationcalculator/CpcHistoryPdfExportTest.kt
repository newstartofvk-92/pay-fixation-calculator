package com.niyammitra.payfixationcalculator

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CpcHistoryPdfExportTest {
    @Test
    fun nullOutputStreamIsAnExportFailure() {
        val error = assertThrows(IOException::class.java) {
            writeCpcHistoryPdf(null) { error("writer must not be called") }
        }
        assertEquals("Could not open the selected file.", error.message)
    }

    @Test
    fun successfulPdfWriteProducesBytesAndClosesTheOutputStream() {
        val bytes = byteArrayOf(37, 80, 68, 70, 45)
        val output = CloseTrackingOutputStream()

        writeCpcHistoryPdf(output) { stream -> stream.write(bytes) }

        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(true, output.closed)
    }

    private class CloseTrackingOutputStream : ByteArrayOutputStream() {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }
}
