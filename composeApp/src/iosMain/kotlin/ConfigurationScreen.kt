import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import platform.Foundation.NSUserDefaults

@Composable
fun ConfigurationScreen(modifier: Modifier = Modifier) {
    val userDefaults = NSUserDefaults.standardUserDefaults
    val focusManager = LocalFocusManager.current
    var configuration by remember { mutableStateOf(AppConfiguration.loadFrom(userDefaults)) }

    Column(modifier) {
        TextField(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            value = configuration.pipedApiInstanceUrl ?: "",
            onValueChange = { configuration = configuration.copy(pipedApiInstanceUrl = it) },
            label = { Text("Piped API URL") }
        )
        Button(onClick = {
            configuration.saveTo(userDefaults)
            focusManager.clearFocus()
        }, modifier = Modifier.padding(4.dp)) {
            Text("Save")
        }
    }
}