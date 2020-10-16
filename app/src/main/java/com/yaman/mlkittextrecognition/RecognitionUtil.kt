package com.yaman.mlkittextrecognition

import android.graphics.Bitmap
import android.text.Editable
import androidx.appcompat.widget.AppCompatEditText
import com.huawei.hmf.tasks.Task
import com.huawei.hms.mlsdk.MLAnalyzerFactory
import com.huawei.hms.mlsdk.common.MLFrame
import com.huawei.hms.mlsdk.text.MLLocalTextSetting
import com.huawei.hms.mlsdk.text.MLRemoteTextSetting
import com.huawei.hms.mlsdk.text.MLText
import com.huawei.hms.mlsdk.text.MLTextAnalyzer
import java.io.IOException

class RecognitionUtil {


    companion object {

        var mTextAnalyzer: MLTextAnalyzer? = null
        var recognitionTextResult: String? = ""


        fun asyncAnalyzeTextLocal(
            bitmap: Bitmap,
            etResult: AppCompatEditText
        ): String? {

            val settingLocal = MLLocalTextSetting.Factory()
                .setOCRMode(MLLocalTextSetting.OCR_DETECT_MODE)
                .setLanguage("en")
                .setLanguage("tr")
                .create()
            mTextAnalyzer = MLAnalyzerFactory.getInstance().getLocalTextAnalyzer(settingLocal)
            return asyncAnalyseFrame(bitmap,etResult)
        }

        fun asyncAnalyzeTextCloud(
            bitmap: Bitmap,
            etResult: AppCompatEditText
        ): String? {
            //Bir analizör oluşturun. Önerilen yol, MLRemoteTextSetting kullanmaktır. Bu şekilde, daha doğru metin tanıma için tanınacak dilleri belirleyebilirsiniz.
            val languageList: MutableList<String> = ArrayList()
            languageList.add("en")
            languageList.add("tr")
            // Bulut üzerinde metin algılama modunu ayarlayın.
            // MLRemoteTextSetting.OCR_COMPACT_SCENE: yoğun metin tanıma
            // MLRemoteTextSetting.OCR_LOOSE_SCENE: seyrek metin tanıma
            val settingRemote =
                MLRemoteTextSetting.Factory()
                    .setTextDensityScene(MLRemoteTextSetting.OCR_COMPACT_SCENE)
                    .setLanguageList(languageList)
                    .setBorderType(MLRemoteTextSetting.ARC)
                    .setTextDensityScene(MLRemoteTextSetting.OCR_LOOSE_SCENE)
                    // ISO 639-1 ile uyumlu olması gereken, tanınabilecek dilleri belirtin.
                    // Döndürülen metin kenarlık kutusunun biçimini ayarlayın.
                    // MLRemoteTextSetting.NGON: Dörtgenin dört köşesinin koordinatlarını döndür.
                    // MLRemoteTextSetting.ARC: Bir yaydaki çokgen kenarlığın köşelerini döndürür. 72 köşeye kadar koordinatlar döndürülebilir.
                    // Yöntem 2: Metin tanıma için dilleri otomatik olarak algılamak üzere varsayılan parametre ayarlarını kullanın. Bu yöntem, seyrek metin senaryoları için geçerlidir. Döndürülen metin kutusunun biçimi MLRemoteTextSetting.NGON şeklindedir.
                    .create()
            mTextAnalyzer = MLAnalyzerFactory.getInstance().getRemoteTextAnalyzer(settingRemote)

            return asyncAnalyseFrame(bitmap, etResult)
        }


        private fun asyncAnalyseFrame(
            bitmap: Bitmap,
            etResult: AppCompatEditText
        ): String? {
            val frame = MLFrame.fromBitmap(bitmap)

            val task: Task<MLText> = mTextAnalyzer!!.asyncAnalyseFrame(frame)
            task.addOnSuccessListener {
                recognitionTextResult = it.stringValue
                etResult.text = Editable.Factory.getInstance().newEditable(recognitionTextResult)
                mTextAnalyzer!!.stop()
            }.addOnFailureListener { e ->
                recognitionTextResult = e.message
            }
            return recognitionTextResult
        }

        fun releaseAnalyzer() {
            if (mTextAnalyzer != null) {
                try {
                    mTextAnalyzer!!.stop()
                } catch (e: IOException) {
                }
            }
        }

    }
}