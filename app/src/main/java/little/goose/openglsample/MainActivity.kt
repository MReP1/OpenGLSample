package little.goose.openglsample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import little.goose.openglsample.ui.image.OpenGLImageScreen
import little.goose.openglsample.ui.image.OpenGLVideoEncodeScreen
import little.goose.openglsample.ui.theme.OpenGLSampleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenGLSampleTheme {
                OpenGLVideoEncodeScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}