package com.batsd.jmcomict.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.batsd.jmcomict.R

/**
 * 可复用的免责声明弹窗。
 *
 * @param visible 是否显示
 * @param onAgree 用户点击同意时回调
 * @param onDisagree 用户点击不同意时回调
 * @param title 可自定义标题（默认使用资源）
 * @param text 可自定义正文（默认使用资源）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisclaimerDialog(
    visible: Boolean,
    onAgree: () -> Unit,
    onDisagree: () -> Unit,
    title: String = stringResource(R.string.disclaimer_title),
    text: String = stringResource(R.string.disclaimer_text)
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = { /* block dismiss */ },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
                    .clearAndSetSemantics { contentDescription = title }
            ) {
                Text(text)
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree) { Text(stringResource(R.string.agree)) }
        },
        dismissButton = {
            TextButton(onClick = onDisagree) { Text(stringResource(R.string.disagree)) }
        }
    )
}
