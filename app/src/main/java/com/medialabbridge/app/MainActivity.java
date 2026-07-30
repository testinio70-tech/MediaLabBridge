package com.medialabbridge.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String PREFS = "bridge_preferences";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText serverInput;
    private EditText tokenInput;
    private EditText textInput;
    private Spinner actionSpinner;
    private TextView statusView;
    private Button sendButton;
    private Button healthButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContentView());
        restoreSettings();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildContentView() {
        int padding = dp(20);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.medialabbridge_logo);
        logo.setContentDescription("Logo de MediaLabBridge");
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams logoParams = matchWrap();
        logoParams.height = dp(170);
        content.addView(logo, logoParams);

        TextView title = new TextView(this);
        title.setText("MediaLabBridge");
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Envía texto y comandos al receptor de Windows dentro de tu red local.");
        subtitle.setTextSize(15);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        content.addView(subtitle, matchWrap());

        content.addView(label("Dirección del PC"), matchWrap());
        serverInput = new EditText(this);
        serverInput.setHint("Ejemplo: 192.168.1.20:8765");
        serverInput.setSingleLine(true);
        serverInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        content.addView(serverInput, matchWrap());
        content.addView(quickBar(
                quickButton("Pegar dirección", v -> pasteInto(serverInput, true)),
                quickButton("Limpiar", v -> serverInput.setText(""))
        ), matchWrap());

        content.addView(label("Token del receptor"), matchWrapWithTopMargin(12));
        tokenInput = new EditText(this);
        tokenInput.setHint("Pega aquí el token que muestra el programa de Windows");
        tokenInput.setSingleLine(true);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(tokenInput, matchWrap());
        content.addView(quickBar(
                quickButton("Pegar token", v -> pasteInto(tokenInput, true)),
                quickButton("Limpiar", v -> tokenInput.setText(""))
        ), matchWrap());

        healthButton = new Button(this);
        healthButton.setText("Comprobar conexión");
        healthButton.setOnClickListener(v -> checkHealth());
        content.addView(healthButton, matchWrapWithTopMargin(12));

        content.addView(label("Acción"), matchWrapWithTopMargin(16));
        actionSpinner = new Spinner(this);
        String[] actions = {"Copiar al portapapeles", "Ejecutar en PowerShell"};
        actionSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, actions));
        content.addView(actionSpinner, matchWrap());

        content.addView(label("Texto o comando"), matchWrapWithTopMargin(16));
        textInput = new EditText(this);
        textInput.setHint("Escribe o pega aquí el contenido que enviarás al PC");
        textInput.setGravity(Gravity.TOP | Gravity.START);
        textInput.setMinLines(9);
        textInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        content.addView(textInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        content.addView(quickBar(
                quickButton("Seleccionar todo", v -> selectAllText(textInput)),
                quickButton("Copiar", v -> copySelectionOrAll(textInput, "Comando MediaLabBridge")),
                quickButton("Pegar", v -> pasteInto(textInput, false)),
                quickButton("Limpiar", v -> textInput.setText(""))
        ), matchWrap());

        sendButton = new Button(this);
        sendButton.setText("Enviar al PC");
        sendButton.setOnClickListener(v -> sendCommand());
        content.addView(sendButton, matchWrapWithTopMargin(14));

        statusView = new TextView(this);
        statusView.setText("Estado: sin conectar");
        statusView.setTextIsSelectable(true);
        statusView.setPadding(0, dp(16), 0, dp(4));
        content.addView(statusView, matchWrap());

        content.addView(quickBar(
                quickButton("Seleccionar respuesta", v -> selectAllText(statusView)),
                quickButton("Copiar respuesta", v -> copySelectionOrAll(statusView, "Respuesta MediaLabBridge")),
                quickButton("Limpiar estado", v -> statusView.setText("Estado: listo"))
        ), matchWrapWithBottomMargin(30));

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        return scrollView;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextSize(14);
        return view;
    }

    private Button quickButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private HorizontalScrollView quickBar(Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        for (Button button : buttons) {
            row.addView(button, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.addView(row);
        return scroller;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams matchWrapWithTopMargin(int marginDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(marginDp);
        return params;
    }

    private LinearLayout.LayoutParams matchWrapWithBottomMargin(int marginDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(marginDp);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void restoreSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        serverInput.setText(prefs.getString("server", ""));
        tokenInput.setText(prefs.getString("token", ""));
    }

    private void saveSettings() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString("server", serverInput.getText().toString().trim())
                .putString("token", tokenInput.getText().toString().trim())
                .apply();
    }

    private ClipboardManager clipboardManager() {
        return (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    }

    private void selectAllText(TextView view) {
        if (view.getText() == null || view.getText().length() == 0) {
            showToast("No hay texto para seleccionar.");
            return;
        }

        view.requestFocus();
        if (view instanceof EditText) {
            ((EditText) view).selectAll();
            return;
        }

        view.setText(view.getText(), TextView.BufferType.SPANNABLE);
        CharSequence current = view.getText();
        if (current instanceof Spannable) {
            Selection.selectAll((Spannable) current);
        }
    }

    private void copySelectionOrAll(TextView view, String label) {
        CharSequence source = view.getText();
        int start = -1;
        int end = -1;

        if (view instanceof EditText) {
            start = ((EditText) view).getSelectionStart();
            end = ((EditText) view).getSelectionEnd();
        } else if (source instanceof Spannable) {
            start = Selection.getSelectionStart(source);
            end = Selection.getSelectionEnd(source);
        }

        String value = TextActions.selectedOrAll(source, start, end);
        if (value.isEmpty()) {
            showToast("No hay texto para copiar.");
            return;
        }

        clipboardManager().setPrimaryClip(ClipData.newPlainText(label, value));
        showToast("Texto copiado.");
    }

    private void pasteInto(EditText target, boolean trimWhitespace) {
        ClipboardManager manager = clipboardManager();
        if (!manager.hasPrimaryClip() || manager.getPrimaryClip() == null
                || manager.getPrimaryClip().getItemCount() == 0) {
            showToast("El portapapeles está vacío.");
            return;
        }

        CharSequence pasted = manager.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (pasted == null) {
            showToast("No se pudo leer el portapapeles.");
            return;
        }

        String value = trimWhitespace ? pasted.toString().trim() : pasted.toString();
        int start = TextActions.safeInsertionIndex(target.getText(), target.getSelectionStart());
        int end = TextActions.safeInsertionIndex(target.getText(), target.getSelectionEnd());
        int from = Math.min(start, end);
        int to = Math.max(start, end);
        target.getText().replace(from, to, value);
        target.requestFocus();
        target.setSelection(from + value.length());
        showToast("Texto pegado.");
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String normalizedBaseUrl(String rawValue) {
        return TextActions.normalizedBaseUrl(rawValue);
    }

    private void checkHealth() {
        final String endpoint;
        try {
            endpoint = normalizedBaseUrl(serverInput.getText().toString()) + "/health";
        } catch (IllegalArgumentException error) {
            statusView.setText("Error: " + error.getMessage());
            return;
        }

        setBusy(true, "Comprobando conexión...");
        saveSettings();
        executor.execute(() -> {
            try {
                HttpResult result = request("GET", endpoint, null, null);
                showResult("Conexión: HTTP " + result.code + "\n" + result.body);
            } catch (Exception error) {
                showError(error);
            }
        });
    }

    private void sendCommand() {
        final String text = textInput.getText().toString();
        final String token = tokenInput.getText().toString().trim();
        final String action = actionSpinner.getSelectedItemPosition() == 0 ? "copy" : "execute";
        final String endpoint;

        if (text.trim().isEmpty()) {
            statusView.setText("Estado: escribe algún texto o comando.");
            return;
        }
        if (token.isEmpty()) {
            statusView.setText("Estado: escribe el token del receptor.");
            return;
        }
        try {
            endpoint = normalizedBaseUrl(serverInput.getText().toString()) + "/api/v1/command";
        } catch (IllegalArgumentException error) {
            statusView.setText("Error: " + error.getMessage());
            return;
        }

        setBusy(true, "Enviando...");
        saveSettings();
        executor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("requestId", UUID.randomUUID().toString());
                payload.put("action", action);
                payload.put("text", text);

                HttpResult result = request("POST", endpoint, payload.toString(), token);
                showResult("Respuesta: HTTP " + result.code + "\n" + result.body);
            } catch (Exception error) {
                showError(error);
            }
        });
    }

    private HttpResult request(String method, String endpoint, String body, String bearerToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(70000);
        connection.setRequestProperty("Accept", "application/json");

        if (bearerToken != null) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }

        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = readAll(stream);
        connection.disconnect();
        return new HttpResult(code, response);
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append('\n');
            }
        }
        return result.toString().trim();
    }

    private void setBusy(boolean busy, String message) {
        sendButton.setEnabled(!busy);
        healthButton.setEnabled(!busy);
        statusView.setText("Estado: " + message);
    }

    private void showResult(String message) {
        runOnUiThread(() -> {
            setBusy(false, "listo");
            statusView.setText(message);
        });
    }

    private void showError(Exception error) {
        runOnUiThread(() -> {
            setBusy(false, "error");
            statusView.setText("Error: " + error.getMessage());
        });
    }

    private static final class HttpResult {
        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }
}
