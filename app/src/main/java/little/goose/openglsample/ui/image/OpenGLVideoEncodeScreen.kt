package little.goose.openglsample.ui.image

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import little.goose.openglsample.ui.video.VideoEncodeViewModel

@Composable
fun OpenGLVideoEncodeScreen(
    modifier: Modifier = Modifier
) {
    val viewModel = viewModel<VideoEncodeViewModel>()
    LaunchedEffect(Unit) {
        viewModel.encode()
    }
    Surface(modifier = modifier) {

    }
}