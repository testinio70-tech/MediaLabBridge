package com.medialabbridge.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
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

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String PREFS = "bridge_preferences";
    private static final int MAX_COMMAND_CHARACTERS = 2_000_000;
    private static final int[] TIMEOUT_SECONDS = {60, 300, 900, 1_800};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText serverInput;
    private EditText tokenInput;
    private EditText workingDirectoryInput;
    private EditText textInput;
    private Spinner actionSpinner;
    private Spinner timeoutSpinner;
    private TextView statusView;
    private TextView counterView;
    private Button sendButton;
    private Button healthButton;
    private Button recoverButton;
    private String lastRequestId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(13, 17, 23));
        getWindow().setNavigationBarColor(Color.rgb(13, 17, 23));
        setContentView(buildScreen());
        restoreSettings();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildScreen() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));
        content.setBackgroundColor(Color.rgb(13, 17, 23));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.medialabbridge_logo);
        logo.setContentDescription("Logo de MediaLabBridge");
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams logoParams = matchWrap();
        logoParams.height = dp(135);
        content.addView(logo, logoParams);

        TextView title = label("MediaLabBridge", 27);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(title, matchWrap());
        TextView subtitle = muted("Dark Stability 0.3.0 · scripts extensos, reintentos y recuperación.");
        subtitle.setPadding(0, dp(3), 0, dp(16));
        content.addView(subtitle, matchWrap());

        content.addView(section("Dirección del PC"), matchWrap());
        serverInput = edit("192.168.1.20:8765", true);
        serverInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        content.addView(serverInput, matchWrap());
        content.addView(bar(button("Pegar dirección", v -> paste(serverInput, true)), button("Limpiar", v -> serverInput.setText(""))), matchWrap());

        content.addView(section("Token del receptor"), top(12));
        tokenInput = edit("Pega el token mostrado en Windows", true);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(tokenInput, matchWrap());
        content.addView(bar(button("Pegar token", v -> paste(tokenInput, true)), button("Limpiar", v -> tokenInput.setText(""))), matchWrap());

        healthButton = primary("Comprobar conexión", v -> checkHealth());
        recoverButton = button("Recuperar último trabajo", v -> recoverLastJob());
        content.addView(healthButton, top(12));
        content.addView(recoverButton, top(5));

        content.addView(section("Acción"), top(16));
        actionSpinner = spinner(new String[]{"Copiar al portapapeles", "Ejecutar script PowerShell"});
        content.addView(actionSpinner, matchWrap());

        content.addView(section("Carpeta de trabajo en Windows"), top(16));
        workingDirectoryInput = edit("Vacío = usuario; ejemplo: C:\\MediaLab", true);
        content.addView(workingDirectoryInput, matchWrap());
        content.addView(bar(
                button("Pegar carpeta", v -> paste(workingDirectoryInput, true)),
                button("Usar C:\\MediaLab", v -> workingDirectoryInput.setText("C:\\MediaLab")),
                button("Predeterminada", v -> workingDirectoryInput.setText(""))), matchWrap());

        content.addView(section("Tiempo máximo"), top(16));
        timeoutSpinner = spinner(new String[]{"1 minuto", "5 minutos", "15 minutos", "30 minutos"});
        timeoutSpinner.setSelection(2);
        content.addView(timeoutSpinner, matchWrap());

        content.addView(section("Texto o script PowerShell"), top(16));
        textInput = edit("Pega un script completo, con varias líneas y funciones.", false);
        textInput.setMinLines(12);
        textInput.setGravity(Gravity.TOP | Gravity.START);
        textInput.setTypeface(Typeface.MONOSPACE);
        textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        textInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_COMMAND_CHARACTERS)});
        content.addView(textInput, matchWrap());

        counterView = muted("0 / 2,000,000 caracteres");
        counterView.setGravity(Gravity.END);
        content.addView(counterView, matchWrap());
        textInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void afterTextChanged(Editable s) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                counterView.setText(String.format(Locale.getDefault(), "%,d / %,d caracteres", s.length(), MAX_COMMAND_CHARACTERS));
            }
        });
        content.addView(bar(
                button("Seleccionar todo", v -> textInput.selectAll()),
                button("Copiar", v -> copy(textInput, "Script MediaLabBridge")),
                button("Pegar", v -> paste(textInput, false)),
                button("Limpiar", v -> textInput.setText(""))), matchWrap());

        sendButton = primary("Enviar al PC", v -> sendCommand());
        content.addView(sendButton, top(14));

        statusView = label("Estado: sin conectar", 13);
        statusView.setTypeface(Typeface.MONOSPACE);
        statusView.setTextIsSelectable(true);
        statusView.setMinLines(5);
        statusView.setPadding(dp(12), dp(12), dp(12), dp(12));
        statusView.setBackgroundColor(Color.rgb(22, 27, 34));
        content.addView(statusView, top(14));
        content.addView(bar(
                button("Seleccionar respuesta", v -> selectAll(statusView)),
                button("Copiar respuesta", v -> copy(statusView, "Respuesta MediaLabBridge")),
                button("Limpiar estado", v -> statusView.setText("Estado: listo"))), bottom(30));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        return scroll;
    }

    private TextView label(String text, float size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(230, 237, 243));
        return view;
    }

    private TextView section(String text) {
        TextView view = label(text, 14);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView muted(String text) {
        TextView view = label(text, 12);
        view.setTextColor(Color.rgb(139, 148, 158));
        return view;
    }

    private EditText edit(String hint, boolean singleLine) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setHintTextColor(Color.rgb(139, 148, 158));
        view.setTextColor(Color.rgb(230, 237, 243));
        view.setSingleLine(singleLine);
        return view;
    }

    private Spinner spinner(String[] values) {
        Spinner view = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        view.setAdapter(adapter);
        return view;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button view = new Button(this);
        view.setText(text);
        view.setAllCaps(false);
        view.setOnClickListener(listener);
        return view;
    }

    private Button primary(String text, View.OnClickListener listener) {
        Button view = button(text, listener);
        view.setTextColor(Color.WHITE);
        view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(31, 111, 235)));
        return view;
    }

    private HorizontalScrollView bar(Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (Button item : buttons) {
            row.addView(item, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(row);
        return scroll;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams top(int value) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(value);
        return params;
    }

    private LinearLayout.LayoutParams bottom(int value) {
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(value);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void restoreSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        serverInput.setText(prefs.getString("server", ""));
        tokenInput.setText(prefs.getString("token", ""));
        workingDirectoryInput.setText(prefs.getString("workingDirectory", ""));
        timeoutSpinner.setSelection(Math.max(0, Math.min(3, prefs.getInt("timeoutIndex", 2))));
        lastRequestId = prefs.getString("lastRequestId", "");
        recoverButton.setEnabled(!lastRequestId.isEmpty());
    }

    private void saveSettings() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("server", serverInput.getText().toString().trim())
                .putString("token", tokenInput.getText().toString().trim())
                .putString("workingDirectory", workingDirectoryInput.getText().toString().trim())
                .putInt("timeoutIndex", timeoutSpinner.getSelectedItemPosition())
                .putString("lastRequestId", lastRequestId)
                .apply();
    }

    private void paste(EditText target, boolean trim) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (!manager.hasPrimaryClip() || manager.getPrimaryClip() == null || manager.getPrimaryClip().getItemCount() == 0) {
            toast("El portapapeles está vacío.");
            return;
        }
        CharSequence value = manager.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (value == null) return;
        String text = trim ? value.toString().trim() : value.toString();
        int start = TextActions.safeInsertionIndex(target.getText(), target.getSelectionStart());
        int end = TextActions.safeInsertionIndex(target.getText(), target.getSelectionEnd());
        int from = Math.min(start, end);
        target.getText().replace(from, Math.max(start, end), text);
        target.setSelection(Math.min(from + text.length(), target.length()));
        toast("Texto pegado.");
    }

    private void selectAll(TextView source) {
        if (source.getText() == null || source.getText().length() == 0) {
            toast("No hay texto para seleccionar.");
            return;
        }
        if (source instanceof EditText) {
            ((EditText) source).selectAll();
            return;
        }
        source.setText(source.getText(), TextView.BufferType.SPANNABLE);
        if (source.getText() instanceof Spannable) {
            Selection.selectAll((Spannable) source.getText());
        }
    }

    private void copy(TextView source, String label) {
        CharSequence value = source.getText();
        int start = -1;
        int end = -1;
        if (source instanceof EditText) {
            EditText edit = (EditText) source;
            start = edit.getSelectionStart();
            end = edit.getSelectionEnd();
        } else if (value instanceof Spannable) {
            start = Selection.getSelectionStart(value);
            end = Selection.getSelectionEnd(value);
        }
        String text = TextActions.selectedOrAll(value, start, end);
        if (text.isEmpty()) {
            toast("No hay texto para copiar.");
            return;
        }
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText(label, text));
        toast("Texto copiado.");
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String baseUrl() {
        return TextActions.normalizedBaseUrl(serverInput.getText().toString());
    }

    private void checkHealth() {
        final String endpoint;
        try {
            endpoint = baseUrl() + "/health";
        } catch (IllegalArgumentException error) {
            statusView.setText("Error: " + error.getMessage());
            return;
        }
        setBusy(true, "Comprobando conexión...");
        saveSettings();
        executor.execute(() -> {
            try {
                BridgeHttpClient.Result result = call("GET", endpoint, null, null, 15_000);
                show("Conexión: HTTP " + result.code + "\n" + result.body);
            } catch (Exception error) {
                fail(error, null);
            }
        });
    }

    private void sendCommand() {
        final String text = textInput.getText().toString();
        final String token = tokenInput.getText().toString().trim();
        if (text.trim().isEmpty() || token.isEmpty()) {
            statusView.setText("Estado: escribe el script y el token.");
            return;
        }
        final String base;
        try {
            base = baseUrl();
        } catch (IllegalArgumentException error) {
            statusView.setText("Error: " + error.getMessage());
            return;
        }
        final String action = actionSpinner.getSelectedItemPosition() == 0 ? "copy" : "execute";
        final int timeout = TIMEOUT_SECONDS[timeoutSpinner.getSelectedItemPosition()];
        final String workingDirectory = workingDirectoryInput.getText().toString().trim();
        final String requestId = UUID.randomUUID().toString();
        lastRequestId = requestId;
        saveSettings();
        setBusy(true, "Enviando trabajo " + requestId.substring(0, 8) + "...");

        executor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("requestId", requestId);
                payload.put("action", action);
                payload.put("text", text);
                payload.put("timeoutSeconds", timeout);
                payload.put("workingDirectory", workingDirectory);
                int readTimeout = action.equals("execute") ? timeout * 1_000 + 60_000 : 60_000;
                BridgeHttpClient.Result result = call("POST", base + "/api/v1/command", payload.toString(), token, readTimeout);
                show("Trabajo: " + requestId + "\nHTTP " + result.code + "\n" + result.body);
            } catch (Exception error) {
                fail(error, requestId);
            }
        });
    }

    private void recoverLastJob() {
        final String token = tokenInput.getText().toString().trim();
        if (lastRequestId.isEmpty() || token.isEmpty()) {
            statusView.setText("Estado: no hay trabajo recuperable o falta el token.");
            return;
        }
        final String endpoint;
        try {
            endpoint = baseUrl() + "/api/v1/jobs/" + lastRequestId;
        } catch (IllegalArgumentException error) {
            statusView.setText("Error: " + error.getMessage());
            return;
        }
        setBusy(true, "Recuperando " + lastRequestId.substring(0, 8) + "...");
        executor.execute(() -> {
            try {
                BridgeHttpClient.Result result = call("GET", endpoint, null, token, 20_000);
                show("Trabajo: " + lastRequestId + "\nHTTP " + result.code + "\n" + result.body);
            } catch (Exception error) {
                fail(error, lastRequestId);
            }
        });
    }

    private BridgeHttpClient.Result call(String method, String endpoint, String body, String token, int timeout) throws Exception {
        return BridgeHttpClient.requestWithRetry(method, endpoint, body, token, timeout,
                (attempt, total) -> runOnUiThread(() -> statusView.setText(
                        "Estado: conexión interrumpida; reintento " + attempt + "/" + total + "...")));
    }

    private void setBusy(boolean busy, String message) {
        sendButton.setEnabled(!busy);
        healthButton.setEnabled(!busy);
        recoverButton.setEnabled(!busy && !lastRequestId.isEmpty());
        if (busy) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        statusView.setText("Estado: " + message);
    }

    private void show(String message) {
        runOnUiThread(() -> {
            setBusy(false, "listo");
            statusView.setText(message);
        });
    }

    private void fail(Exception error, String requestId) {
        runOnUiThread(() -> {
            setBusy(false, "error");
            String recovery = requestId == null ? "" : "\nTrabajo: " + requestId + "\nUsa ‘Recuperar último trabajo’ al reconectar.";
            statusView.setText("Error: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()) + recovery);
        });
    }
}
