package com.fengluanchui.fengsuan;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.inputmethod.InputMethodManager;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 8, 12);
    private static final int PANEL = Color.rgb(18, 20, 27);
    private static final int PANEL_2 = Color.rgb(25, 28, 37);
    private static final int GOLD = Color.rgb(214, 178, 94);
    private static final int TEXT = Color.rgb(238, 239, 234);
    private static final int MUTED = Color.rgb(151, 158, 170);
    private static final int DANGER = Color.rgb(225, 96, 84);

    private LinearLayout content;
    private EditText editor;
    private TextView preview;
    private String draftText = "";
    private int layoutMode = 0;
    private int soundMode = 1;
    private boolean paperExpanded = false;
    private boolean sharePanelOpen = false;
    private float editorTextSize = 28f;
    private long lastPaperTap = 0;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private Bitmap femaleVoiceBitmap;
    private long lastAcTap = 0;
    private String lastCalculationResult = "";
    private byte[] mechanicalClick;
    private final List<String> undoStack = new ArrayList<>();
    private final List<String> redoStack = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private SharedPreferences prefs;
    private final DecimalFormat df = new DecimalFormat("0.############");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("fengsuan", MODE_PRIVATE);
        loadHistory();
        draftText = prefs.getString("draft", "");
        layoutMode = prefs.getInt("layoutMode", 0);
        soundMode = prefs.contains("soundMode")
                ? prefs.getInt("soundMode", 1)
                : (prefs.getBoolean("toneOn", true) ? 1 : 0);
        mechanicalClick = buildMechanicalClick();
        femaleVoiceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.female_voice);
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.CHINA);
                tts.setPitch(1.28f);
                tts.setSpeechRate(1.08f);
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int outerPad = dp(5);
        root.setPadding(outerPad, outerPad, outerPad, outerPad);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(
                    outerPad + insets.getSystemWindowInsetLeft(),
                    outerPad + insets.getSystemWindowInsetTop(),
                    outerPad + insets.getSystemWindowInsetRight(),
                    outerPad + insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.setBackgroundColor(BG);
        root.setFocusableInTouchMode(true);
        setContentView(root);
        root.requestApplyInsets();
        root.requestFocus();

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(7), dp(7), dp(7), dp(8));
        content.setBackground(new CalculatorShellDrawable());
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        showCalculator();
    }

    @Override
    protected void onPause() {
        super.onPause();
        captureDraft();
        prefs.edit()
                .putString("draft", draftText)
                .putInt("layoutMode", layoutMode)
                .putInt("soundMode", soundMode)
                .apply();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private void showCalculator() {
        captureDraft();
        content.removeAllViews();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(4), dp(4), dp(4), dp(5));
        card.setBackground(new PaperFrameDrawable());

        editor = new EditText(this);
        editor.setTextColor(Color.rgb(47, 48, 51));
        editor.setHintTextColor(Color.rgb(145, 145, 145));
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setMinLines(4);
        editor.setMaxLines(12);
        editor.setSingleLine(false);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setHint("提示：输入 12元×35，再按=号；结果会留在同一行。算完后直接按 + - × ÷ 会自动接着上次结果。连续按两次 AC 才会清空全部内容。");
        editor.setBackground(new PaperDrawable());
        editor.setPadding(dp(14), dp(12), dp(56), dp(12));
        editor.setShowSoftInputOnFocus(false);
        applyEditorTextSizing();
        editor.setText(draftText);
        editor.setSelection(editor.getText().length());
        ScaleGestureDetector scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                editorTextSize = Math.max(18f, Math.min(42f, editorTextSize * detector.getScaleFactor()));
                applyEditorTextSizing();
                editor.invalidate();
                return true;
            }
        });
        editor.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && event.getPointerCount() == 1) {
                long now = System.currentTimeMillis();
                if (now - lastPaperTap < 320) {
                    paperExpanded = !paperExpanded;
                    sharePanelOpen = paperExpanded;
                    showCalculator();
                }
                lastPaperTap = now;
            }
            return false;
        });

        FrameLayout paperWrap = new FrameLayout(this);
        paperWrap.addView(editor, new FrameLayout.LayoutParams(-1, -1));
        addPaperFloatingButtons(paperWrap);
        card.addView(paperWrap, new LinearLayout.LayoutParams(-1, 0, 1));

        preview = label("结果会显示在这里", 17, GOLD, true);
        preview.setGravity(Gravity.RIGHT);
        preview.setPadding(dp(10), dp(3), dp(10), dp(2));
        if (!paperExpanded) card.addView(preview, new LinearLayout.LayoutParams(-1, dp(30)));
        content.addView(card, new LinearLayout.LayoutParams(-1, 0, 1));

        if (!paperExpanded) {
            String[][] rows = keyboardRows();
            for (String[] row : rows) {
                LinearLayout line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setPadding(0, dp(4), 0, 0);
                for (String s : row) {
                    LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(0, -1, 1);
                    kp.setMargins(dp(3), 0, dp(3), 0);
                    line.addView(key(s), kp);
                }
                content.addView(line, new LinearLayout.LayoutParams(-1, 0, 0.118f));
            }
        }
    }

    private void applyEditorTextSizing() {
        if (editor == null) return;
        editor.setTextSize(editorTextSize);
        Paint.FontMetrics metrics = editor.getPaint().getFontMetrics();
        float naturalLineHeight = metrics.descent - metrics.ascent;
        float extra = Math.max(0f, editorLineHeightPx() - naturalLineHeight);
        editor.setLineSpacing(extra, 1.0f);
    }

    private float editorLineHeightPx() {
        float textPx = editor != null
                ? editor.getTextSize()
                : editorTextSize * getResources().getDisplayMetrics().scaledDensity;
        return Math.max(dp(30), textPx * 1.55f);
    }

    private void addPaperFloatingButtons(FrameLayout paperWrap) {
        if (paperExpanded) {
            Button shareButton = iconButton(R.drawable.ic_share_nodes, dp(40));
            FrameLayout.LayoutParams shareParams = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.BOTTOM | Gravity.LEFT);
            shareParams.setMargins(dp(10), 0, 0, dp(10));
            paperWrap.addView(shareButton, shareParams);

            Button textButton = floatingTextButton();
            FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.BOTTOM | Gravity.LEFT);
            textParams.setMargins(dp(58), 0, 0, dp(12));
            paperWrap.addView(textButton, textParams);

            TextView hint = label("双击返回主界面", 15, Color.WHITE, true);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(dp(16), dp(8), dp(16), dp(8));
            hint.setBackground(round(Color.argb(185, 20, 20, 20), dp(16), 0));
            FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            hp.setMargins(0, 0, 0, dp(18));
            paperWrap.addView(hint, hp);
            hint.postDelayed(() -> hint.setVisibility(View.GONE), 2000);
            return;
        }

        Button drawerButton = floatingTextButton("<", 22);
        drawerButton.setOnClickListener(v -> {
            sharePanelOpen = !sharePanelOpen;
            showCalculator();
        });
        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(dp(34), dp(38), Gravity.BOTTOM | Gravity.LEFT);
        drawerParams.setMargins(dp(8), 0, 0, dp(12));
        paperWrap.addView(drawerButton, drawerParams);

        if (sharePanelOpen) {
            Button shareButton = iconButton(R.drawable.ic_share_nodes, dp(36));
            FrameLayout.LayoutParams shareParams = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.BOTTOM | Gravity.LEFT);
            shareParams.setMargins(dp(48), 0, 0, dp(10));
            paperWrap.addView(shareButton, shareParams);
        }

        Button textButton = floatingTextButton();
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.BOTTOM | Gravity.RIGHT);
        textParams.setMargins(0, 0, dp(10), dp(12));
        paperWrap.addView(textButton, textParams);
    }

    private Button floatingTextButton() {
        return floatingTextButton("文", 24);
    }

    private Button floatingTextButton(String text, int sp) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(sp);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(0, 0, 0, 0);
        b.setTextColor(Color.rgb(48, 49, 51));
        b.setBackground(new KeyCapDrawable(Color.rgb(242, 242, 240), dp(9), true));
        b.setShadowLayer(dp(0.7f), 0, dp(0.8f), Color.argb(80, 255, 255, 255));
        if ("文".equals(text)) b.setOnClickListener(v -> press("文"));
        return b;
    }

    private Button iconButton(int drawableRes, int iconSize) {
        Button b = new Button(this);
        b.setText("");
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setBackground(new KeyCapDrawable(Color.rgb(46, 48, 48), dp(9), false));
        Drawable icon = getResources().getDrawable(drawableRes);
        icon.setBounds(0, 0, iconSize, iconSize);
        b.setCompoundDrawables(null, icon, null, null);
        b.setOnClickListener(v -> showShareEditDialog());
        return b;
    }

    private String[][] keyboardRows() {
        String sound = soundLabel();
        switch (layoutMode) {
            case 1:
                return new String[][]{
                        {"布局", "◀", "▶", "↵", "AC"},
                        {"↶", "↷", "复", "贴", "⌫"},
                        {"()", "7", "8", "9", "÷"},
                        {"xʸ", "4", "5", "6", "×"},
                        {"√", "1", "2", "3", "-"},
                        {"%", "0", ".", "=", "+"},
                        {"帮助", "函数", "常量", sound, "常用"}
                };
            case 2:
                return new String[][]{
                        {"布局", "◀", "▶", "↵", "AC"},
                        {"↶", "↷", "复", "贴", "⌫"},
                        {"sin", "cos", "tan", "lg", "ln"},
                        {"()", "7", "8", "9", "÷"},
                        {"xʸ", "4", "5", "6", "×"},
                        {"√", "1", "2", "3", "-"},
                        {"%", "0", ".", "=", "+"}
                };
            case 3:
                return new String[][]{
                        {"布局", "◀", "▶", "↵", "AC"},
                        {"↶", "↷", "复", "贴", "⌫"},
                        {"()", "7", "8", "9", "÷"},
                        {"xʸ", "4", "5", "6", "×"},
                        {"√", "1", "2", "3", "-"},
                        {"%", "0", ".", "=", "+"}
                };
            case 4:
                return new String[][]{
                        {"布局", sound, "↵", "⌫", "AC"},
                        {"()", "7", "8", "9", "÷"},
                        {"xʸ", "4", "5", "6", "×"},
                        {"√", "1", "2", "3", "-"},
                        {"%", "0", ".", "=", "+"}
                };
            case 5:
                return new String[][]{
                        {"布局", "↵", "⌫", "AC"},
                        {"7", "8", "9", "÷"},
                        {"4", "5", "6", "×"},
                        {"1", "2", "3", "-"},
                        {"0", ".", "=", "+"}
                };
            default:
                return new String[][]{
                        {"布局", "◀", "▶", "↵", "AC"},
                        {"↶", "↷", "复", "贴", "⌫"},
                        {"sin", "cos", "tan", "lg", "ln"},
                        {"()", "7", "8", "9", "÷"},
                        {"xʸ", "4", "5", "6", "×"},
                        {"√", "1", "2", "3", "-"},
                        {"%", "0", ".", "=", "+"},
                        {"帮助", "函数", "常量", sound, "常用"}
                };
        }
    }

    private String soundLabel() {
        if (soundMode == 0) return "静音";
        if (soundMode == 2) return "女声";
        return "机械";
    }

    private View key(String s) {
        if (isSoundKey(s)) return soundKey(s);
        if (isIconKey(s)) return iconKey(s);

        Button b = new Button(this);
        String shown = displayKey(s);
        b.setText(shown);
        b.setTextSize(keyTextSize(s, shown));
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setSingleLine(true);
        b.setMaxLines(1);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(0, 0, 0, 0);
        b.setTextColor(keyTextColor(s));
        b.setBackground(new KeyCapDrawable(keyFill(s), dp(6), isLightKey(s)));
        b.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        if ("↵".equals(s)) b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setOnClickListener(v -> press(s));
        return b;
    }

    private View soundKey(String state) {
        SoundKeyView view = new SoundKeyView(this, state);
        view.setOnClickListener(v -> press(state));
        return view;
    }

    private View iconKey(String key) {
        IconKeyView view = new IconKeyView(this, key);
        view.setOnClickListener(v -> press(key));
        return view;
    }

    private boolean isSoundKey(String s) {
        return "声音".equals(s) || "机械".equals(s) || "女声".equals(s) || "静音".equals(s);
    }

    private boolean isIconKey(String s) {
        return "布局".equals(s) || "◀".equals(s) || "▶".equals(s) || "↵".equals(s) ||
                "↶".equals(s) || "↷".equals(s) || "复".equals(s) || "贴".equals(s) ||
                "⌫".equals(s);
    }

    private int keyTextSize(String raw, String shown) {
        if ("历史".equals(raw) || "函数".equals(raw) || "常量".equals(raw) ||
                "声音".equals(raw) || "机械".equals(raw) || "女声".equals(raw) || "静音".equals(raw) || "常用".equals(raw)) {
            return 19;
        }
        if ("复制".equals(raw) || "保存".equals(raw) || "帮助".equals(raw) ||
                "复".equals(raw) || "贴".equals(raw)) {
            return 18;
        }
        if ("sin".equals(raw) || "cos".equals(raw) || "tan".equals(raw) ||
                "lg".equals(raw) || "ln".equals(raw)) {
            return 17;
        }
        if ("xʸ".equals(raw) || "√".equals(raw) || "()".equals(raw)) {
            return 22;
        }
        if ("AC".equals(raw)) return 24;
        if ("↵".equals(raw)) return 32;
        if ("⌫".equals(raw) || "↶".equals(raw) || "↷".equals(raw) ||
                "◀".equals(raw) || "▶".equals(raw)) {
            return 25;
        }
        if ("=".equals(raw)) return 30;
        if ("+".equals(raw) || "-".equals(raw) || "×".equals(raw) || "÷".equals(raw)) {
            return 29;
        }
        if ("文".equals(raw)) return 25;
        if (raw.matches("[0-9.]")) return 26;
        return shown.length() > 2 ? 16 : 23;
    }

    private String displayKey(String s) {
        if ("复".equals(s)) return "复制";
        if ("贴".equals(s)) return "粘贴";
        if ("√".equals(s)) return "ʸ√x";
        if ("◀".equals(s)) return "◀";
        if ("▶".equals(s)) return "▶";
        return s;
    }

    private void press(String s) {
        if (editor == null) return;
        if ("声音".equals(s) || "机械".equals(s) || "女声".equals(s) || "静音".equals(s)) {
            cycleSoundMode();
            return;
        }
        if (!"=".equals(s)) playKeyFeedback(s);
        switch (s) {
            case "布局":
                showLayoutDialog();
                break;
            case "AC":
                handleAcPress();
                break;
            case "⌫":
                int pos = Math.max(0, editor.getSelectionStart());
                if (pos > 0) {
                    pushUndo();
                    editor.getText().delete(pos - 1, pos);
                    captureDraft();
                }
                break;
            case "=":
                calculateAndAppend();
                break;
            case "◀":
                moveCursor(-1);
                break;
            case "▶":
                moveCursor(1);
                break;
            case "↵":
                insert("\n");
                break;
            case "↶":
                undo();
                break;
            case "↷":
                redo();
                break;
            case "保存":
                saveCurrentText();
                break;
            case "复制":
            case "复":
                showCopyDialog();
                break;
            case "贴":
                pasteClipboard();
                break;
            case "文":
                showTextInsertDialog();
                break;
            case "帮助":
                showHelpDialog();
                break;
            case "历史":
                showHistoryDialog();
                break;
            case "函数":
                showFunctionDialog();
                break;
            case "常量":
                showConstantDialog();
                break;
            case "常用":
                showCommonDialog();
                break;
            case "()":
                insert("()");
                moveCursor(-1);
                break;
            case "xʸ":
                insert("^");
                break;
            case "√":
                insert("sqrt(");
                break;
            case "sin":
            case "cos":
            case "tan":
            case "lg":
            case "ln":
                insert(s + "(");
                break;
            case "π":
                insert("π");
                break;
            case "大写":
                try {
                    String line = currentLine();
                    double v = evaluate(line);
                    preview.setText(rmb(v));
                } catch (Exception ex) {
                    preview.setText("请先输入金额");
                }
                break;
            default:
                if (isBasicOperator(s) && shouldCarryLastAnswer()) {
                    insert(lastAnswerForCarry() + s);
                    break;
                }
                insert(s);
        }
    }

    private void handleAcPress() {
        long now = System.currentTimeMillis();
        if (now - lastAcTap > 1200) {
            lastAcTap = now;
            preview.setText("再按一次 AC 清空全部内容");
            Toast.makeText(this, "再按一次 AC 清空全部内容", Toast.LENGTH_SHORT).show();
            return;
        }
        pushUndo();
        editor.setText("");
        draftText = "";
        lastCalculationResult = "";
        lastAcTap = 0;
        preview.setText("已清空");
    }

    private boolean isBasicOperator(String s) {
        return "+".equals(s) || "-".equals(s) || "×".equals(s) || "÷".equals(s);
    }

    private boolean shouldCarryLastAnswer() {
        String line = currentLine().trim();
        return line.isEmpty() && !lastAnswerForCarry().isEmpty();
    }

    private String lastAnswerForCarry() {
        if (lastCalculationResult != null && !lastCalculationResult.isEmpty()) return lastCalculationResult;
        return lastAnswer();
    }

    private void insert(String s) {
        pushUndo();
        int pos = Math.max(0, editor.getSelectionStart());
        editor.getText().insert(pos, s);
        captureDraft();
    }

    private void moveCursor(int delta) {
        int pos = Math.max(0, editor.getSelectionStart());
        int next = Math.max(0, Math.min(editor.getText().length(), pos + delta));
        editor.setSelection(next);
    }

    private void pushUndo() {
        if (editor == null) return;
        undoStack.add(editor.getText().toString());
        if (undoStack.size() > 40) undoStack.remove(0);
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.add(editor.getText().toString());
        String text = undoStack.remove(undoStack.size() - 1);
        editor.setText(text);
        editor.setSelection(editor.getText().length());
        captureDraft();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.add(editor.getText().toString());
        String text = redoStack.remove(redoStack.size() - 1);
        editor.setText(text);
        editor.setSelection(editor.getText().length());
        captureDraft();
    }

    private void cycleSoundMode() {
        soundMode = (soundMode + 1) % 3;
        prefs.edit().putInt("soundMode", soundMode).apply();
        String message = soundMode == 0 ? "已切换为静音" : (soundMode == 1 ? "已切换为机械键盘音" : "已切换为中文女声");
        if (soundMode == 1) playMechanicalClick();
        if (soundMode == 2) speak("女声模式");
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        showCalculator();
    }

    private void playKeyFeedback(String key) {
        if (soundMode == 0) return;
        if (soundMode == 2) {
            String text = speechForKey(key);
            if (!text.isEmpty()) {
                speak(text);
                return;
            }
        }
        playMechanicalClick();
    }

    private void speak(String text) {
        if (ttsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fengsuan-key-" + System.nanoTime());
        } else {
            playMechanicalClick();
        }
    }

    private String speechForKey(String key) {
        switch (key) {
            case "0": return "零";
            case "1": return "一";
            case "2": return "二";
            case "3": return "三";
            case "4": return "四";
            case "5": return "五";
            case "6": return "六";
            case "7": return "七";
            case "8": return "八";
            case "9": return "九";
            case ".": return "点";
            case "+": return "加";
            case "-": return "减";
            case "×": return "乘";
            case "÷": return "除";
            case "=": return "等于";
            case "%": return "百分号";
            case "AC": return "清空";
            case "⌫": return "删除";
            case "↵": return "换行";
            case "◀": return "左移";
            case "▶": return "右移";
            case "↶": return "撤销";
            case "↷": return "重做";
            case "复制":
            case "复": return "复制";
            case "贴": return "粘贴";
            case "布局": return "布局";
            case "保存": return "保存";
            case "文": return "文本";
            case "历史": return "历史";
            case "函数": return "函数";
            case "常量": return "常量";
            case "常用": return "常用";
            case "()": return "括号";
            case "xʸ": return "幂";
            case "√": return "根号";
            case "sin": return "正弦";
            case "cos": return "余弦";
            case "tan": return "正切";
            case "lg": return "常用对数";
            case "ln": return "自然对数";
            case "π": return "派";
            default: return key;
        }
    }

    private void playMechanicalClick() {
        if (mechanicalClick == null || mechanicalClick.length == 0) return;
        try {
            AudioTrack track = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    22050,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    mechanicalClick.length,
                    AudioTrack.MODE_STATIC);
            track.write(mechanicalClick, 0, mechanicalClick.length);
            track.setVolume(0.92f);
            track.play();
            if (content != null) content.postDelayed(track::release, 90);
        } catch (Exception ignored) {
        }
    }

    private byte[] buildMechanicalClick() {
        int sampleRate = 22050;
        int samples = (int) (sampleRate * 0.045);
        byte[] pcm = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            double t = i / (double) sampleRate;
            double strike = Math.sin(2 * Math.PI * 3200 * t) * Math.exp(-120 * t);
            double snap = Math.sin(2 * Math.PI * 7100 * t) * Math.exp(-230 * t);
            double rebound = i > sampleRate * 0.012
                    ? Math.sin(2 * Math.PI * 1850 * (t - 0.012)) * Math.exp(-105 * (t - 0.012))
                    : 0;
            int pseudo = (i * 1103515245 + 12345) >>> 16;
            double noise = ((pseudo & 0x7fff) / 16384.0 - 1.0) * Math.exp(-190 * t);
            double value = strike * 0.48 + snap * 0.30 + rebound * 0.20 + noise * 0.16;
            short sample = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, (int) (value * 24500)));
            pcm[i * 2] = (byte) (sample & 0xff);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
        }
        return pcm;
    }

    private void captureDraft() {
        if (editor != null) draftText = editor.getText().toString();
    }

    private void calculateAndAppend() {
        String line = currentLine().trim();
        try {
            double value = evaluate(line);
            String result = df.format(value);
            preview.setText(result);
            replaceCurrentLine(line + "=" + result + "\n");
            lastCalculationResult = result;
            addHistory(line + " = " + result);
            announceResult(result);
        } catch (Exception ex) {
            preview.setText("算式还没看懂");
            Toast.makeText(this, "请检查括号、运算符或函数写法", Toast.LENGTH_SHORT).show();
        }
    }

    private void announceResult(String result) {
        if (soundMode == 2) {
            speak("等于" + speechForNumber(result));
        } else if (soundMode == 1) {
            playMechanicalClick();
        }
    }

    private String speechForNumber(String result) {
        return result
                .replace("-", "负")
                .replace(".", "点")
                .replace("E", "乘以十的")
                .replace("e", "乘以十的");
    }

    private String currentLine() {
        String text = editor.getText().toString();
        int pos = Math.max(0, editor.getSelectionStart());
        int start = text.lastIndexOf('\n', Math.max(0, pos - 1));
        int end = text.indexOf('\n', pos);
        if (end < 0) end = text.length();
        return text.substring(start + 1, end).replaceFirst("^=\\s*", "");
    }

    private void replaceCurrentLine(String replacement) {
        pushUndo();
        String text = editor.getText().toString();
        int pos = Math.max(0, editor.getSelectionStart());
        int start = text.lastIndexOf('\n', Math.max(0, pos - 1));
        int end = text.indexOf('\n', pos);
        if (end < 0) end = text.length();
        editor.getText().replace(start + 1, end, replacement);
        int next = start + 1 + replacement.length();
        editor.setSelection(Math.max(0, Math.min(editor.getText().length(), next)));
        captureDraft();
    }

    private double evaluate(String input) {
        String expr = input
                .replace("×", "*")
                .replace("÷", "/")
                .replace("π", "pi")
                .replace("％", "%");
        expr = expr.replaceAll("[^0-9+\\-*/().%^,!a-zA-Z\\u03c0]", "");
        return new Parser(expr).parse();
    }

    private void showTextInsertDialog() {
        EditText input = new EditText(this);
        input.setTextColor(Color.rgb(32, 33, 35));
        input.setTextSize(18);
        input.setMinLines(2);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("输入要插入的文字，例如：房租、午餐、备注说明");
        int pad = dp(14);
        input.setPadding(pad, pad, pad, pad);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("插入文本")
                .setView(input)
                .setPositiveButton("插入", (d, which) -> {
                    String text = input.getText().toString();
                    if (!text.isEmpty()) insert(text);
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        dialog.show();
    }

    private void showCopyDialog() {
        String[] choices = {"复制当前数值", "复制当前行", "复制整个文本", "返回"};
        new AlertDialog.Builder(this)
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) copyText(currentValueText());
                    if (which == 1) copyText(currentLine());
                    if (which == 2) copyText(editor.getText().toString());
                    dialog.dismiss();
                })
                .show();
    }

    private void showHistoryDialog() {
        captureDraft();
        ArrayList<String> items = new ArrayList<>();
        items.add("结果求和");
        items.add("清空历史");
        for (int i = history.size() - 1; i >= 0; i--) items.add(history.get(i));
        new AlertDialog.Builder(this)
                .setTitle("历史记录 · " + history.size() + " 条")
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        Toast.makeText(this, "结果求和：" + df.format(sumHistory()), Toast.LENGTH_LONG).show();
                    } else if (which == 1) {
                        history.clear();
                        persistHistory();
                        Toast.makeText(this, "历史已清空", Toast.LENGTH_SHORT).show();
                    } else {
                        copyText(items.get(which));
                    }
                })
                .show();
    }

    private String currentValueText() {
        try {
            return df.format(evaluate(currentLine()));
        } catch (Exception e) {
            String text = preview == null ? "" : preview.getText().toString();
            return text == null || text.trim().isEmpty() ? currentLine() : text;
        }
    }

    private void pasteClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence text = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (text != null) insert(text.toString());
        }
    }

    private void showFunctionDialog() {
        String[] items = {"sin(", "cos(", "tan(", "lg(", "ln(", "sqrt(", "ANS", "人民币大写"};
        new AlertDialog.Builder(this)
                .setTitle("函数输入")
                .setItems(items, (dialog, which) -> {
                    String item = items[which];
                    if ("ANS".equals(item)) insert(lastAnswer());
                    else if ("人民币大写".equals(item)) press("大写");
                    else insert(item);
                })
                .show();
    }

    private void showConstantDialog() {
        LinkedHashMap<String, String> constants = new LinkedHashMap<>();
        constants.put("圆周率 π", "π");
        constants.put("自然常数 e", "e");
        constants.put("黄金分割 0.618", "0.6180339887");
        constants.put("一亩平方米 666.6667", "666.6667");
        constants.put("一斤克数 500", "500");
        String[] names = constants.keySet().toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("常量输入")
                .setItems(names, (dialog, which) -> insert(constants.get(names[which])))
                .show();
    }

    private void showLayoutDialog() {
        String[] modes = layoutNames();
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(8), dp(8), dp(8), dp(4));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("键盘布局选择")
                .setView(wrap)
                .setNegativeButton("取消", null)
                .create();

        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER);
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER);
                item.setPadding(dp(4), dp(4), dp(4), dp(6));
                item.setBackground(round(index == layoutMode ? Color.rgb(238, 238, 238) : Color.TRANSPARENT,
                        dp(8), index == layoutMode ? GOLD : Color.TRANSPARENT));

                LayoutPreviewView preview = new LayoutPreviewView(this, index);
                item.addView(preview, new LinearLayout.LayoutParams(dp(86), dp(132)));
                int nameColor = index == layoutMode ? Color.rgb(45, 47, 52) : Color.rgb(226, 228, 228);
                TextView name = label(modes[index], 12, nameColor, index == layoutMode);
                name.setGravity(Gravity.CENTER);
                item.addView(name, new LinearLayout.LayoutParams(-1, -2));
                item.setOnClickListener(v -> {
                    layoutMode = index;
                    prefs.edit().putInt("layoutMode", layoutMode).apply();
                    dialog.dismiss();
                    showCalculator();
                });

                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, -2, 1);
                ip.setMargins(dp(4), dp(3), dp(4), dp(3));
                line.addView(item, ip);
            }
            wrap.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }

        dialog.show();
    }

    private void showCommonDialog() {
        String[] items = {"工具箱", "历史记录", "保存草稿", "帮助", "关于风算"};
        new AlertDialog.Builder(this)
                .setTitle("常用")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) showToolMenuDialog();
                    if (which == 1) showHistoryDialog();
                    if (which == 2) saveCurrentText();
                    if (which == 3) showHelpDialog();
                    if (which == 4) showAboutDialog();
                })
                .show();
    }

    private String[] layoutNames() {
        return new String[]{"完整函数", "常规完整", "科学无底栏", "常规无底栏", "紧凑科学", "四列大键盘"};
    }

    private void showToolMenuDialog() {
        String[] items = {"人民币大写", "汇率估算", "进制转换", "小数转分数", "单位换算", "时间换算"};
        new AlertDialog.Builder(this)
                .setTitle("工具箱")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) showNumberToolDialog("人民币大写", "输入金额，例如 523458.26", value -> rmb(value));
                    if (which == 1) showNumberToolDialog("汇率估算", "输入人民币金额，例如 1000", value ->
                            "USD $" + df.format(value / 7.25) + "\n" +
                            "EUR €" + df.format(value / 7.85) + "\n" +
                            "RUB ₽" + df.format(value * 11.1) + "\n" +
                            "JPY ¥" + df.format(value * 21.5));
                    if (which == 2) showIntegerToolDialog();
                    if (which == 3) showFractionToolDialog();
                    if (which == 4) showNumberToolDialog("单位换算", "输入数值，例如 12.5", value ->
                            "长度：" + df.format(value) + " m = " + df.format(value / 1000) + " km\n" +
                            "重量：" + df.format(value) + " kg = " + df.format(value * 2) + " 斤\n" +
                            "面积：" + df.format(value) + " m² = " + df.format(value / 666.6667) + " 亩\n" +
                            "体积：" + df.format(value) + " L = " + df.format(value / 1000) + " m³");
                    if (which == 5) showNumberToolDialog("时间换算", "输入分钟数，例如 135", value ->
                            df.format(value) + " 分钟\n= " + df.format(value / 60) + " 小时\n= " +
                            df.format(value / 1440) + " 天\n= " + df.format(value * 60) + " 秒");
                })
                .show();
    }

    private interface NumberFormatter {
        String format(double value);
    }

    private void showNumberToolDialog(String title, String hint, NumberFormatter formatter) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setTextColor(Color.rgb(32, 33, 35));
        input.setTextSize(18);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("转换", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            ok.setOnClickListener(v -> {
                try {
                    double value = Double.parseDouble(input.getText().toString());
                    new AlertDialog.Builder(this)
                            .setTitle(title)
                            .setMessage(formatter.format(value))
                            .setPositiveButton("复制", (copyDialog, which) -> copyText(formatter.format(value)))
                            .setNegativeButton("关闭", null)
                            .show();
                    dialog.dismiss();
                } catch (Exception e) {
                    input.setError("请输入数字");
                }
            });
            input.requestFocus();
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        dialog.show();
    }

    private void showIntegerToolDialog() {
        showNumberToolDialog("进制转换", "输入十进制整数，例如 255", value -> {
            BigInteger n = BigInteger.valueOf((long) value);
            return "BIN " + n.toString(2) + "\nOCT " + n.toString(8) + "\nHEX " + n.toString(16).toUpperCase(Locale.ROOT);
        });
    }

    private void showFractionToolDialog() {
        EditText input = new EditText(this);
        input.setHint("输入小数，例如 0.125");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setTextColor(Color.rgb(32, 33, 35));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("小数转分数")
                .setView(input)
                .setPositiveButton("转换", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            ok.setOnClickListener(v -> {
                try {
                    String result = toFraction(new BigDecimal(input.getText().toString().trim()));
                    new AlertDialog.Builder(this)
                            .setTitle("小数转分数")
                            .setMessage(result)
                            .setPositiveButton("复制", (copyDialog, which) -> copyText(result))
                            .setNegativeButton("关闭", null)
                            .show();
                    dialog.dismiss();
                } catch (Exception e) {
                    input.setError("请输入小数");
                }
            });
            input.requestFocus();
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        dialog.show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("风算")
                .setMessage("一款懂你的计算管家\n\n设计与制作：风乱吹\n版本：1.0.11")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("风算使用提示")
                .setMessage("主计算区不会主动弹出系统输入法。\n\n点左上角布局图标可切换 6 种键盘；点“文”插入文字备注；点“=”计算当前行。\n\n复制、粘贴、撤销、重做等入口现在使用图标显示。双击稿纸区可切换全屏高度，双指缩放可调整文字大小。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showShareEditDialog() {
        captureDraft();
        EditText input = new EditText(this);
        input.setTextColor(Color.rgb(32, 33, 35));
        input.setHintTextColor(Color.rgb(120, 120, 120));
        input.setTextSize(17);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(8);
        input.setMaxLines(16);
        input.setSingleLine(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(round(Color.rgb(246, 246, 246), dp(8), Color.rgb(185, 185, 185)));
        input.setText(buildShareText());
        input.setSelection(input.getText().length());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("请编辑待分享文本")
                .setView(input)
                .setPositiveButton("分享", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button share = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            share.setOnClickListener(v -> {
                String text = input.getText().toString().trim();
                if (text.isEmpty()) {
                    input.setError("请先输入要分享的内容");
                    return;
                }
                shareText(text);
                dialog.dismiss();
            });
            input.requestFocus();
            input.postDelayed(() -> ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT), 120);
        });
        dialog.show();
    }

    private String buildShareText() {
        String text = draftText == null ? "" : draftText.trim();
        if (!text.isEmpty()) return text;
        return "风算";
    }

    private void shareText(String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(intent, "分享到"));
        } catch (Exception e) {
            Toast.makeText(this, "没有找到可分享的应用", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveCurrentText() {
        captureDraft();
        String text = editor.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "没有可保存的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        addHistory(text);
        Toast.makeText(this, "已保存到历史", Toast.LENGTH_SHORT).show();
    }

    private void showHistory() {
        captureDraft();
        content.removeAllViews();
        LinearLayout top = card();
        TextView title = label("历史记录", 20, TEXT, true);
        top.addView(title);
        TextView stat = label("共 " + history.size() + " 条，可点按复制到剪贴板。", 13, MUTED, false);
        top.addView(stat);
        LinearLayout ops = new LinearLayout(this);
        ops.setOrientation(LinearLayout.HORIZONTAL);
        ops.addView(actionButton("结果求和", () -> toast("结果求和：" + df.format(sumHistory()))), new LinearLayout.LayoutParams(0, dp(48), 1));
        ops.addView(actionButton("清空历史", () -> {
            history.clear();
            persistHistory();
            showHistory();
        }), new LinearLayout.LayoutParams(0, dp(48), 1));
        top.addView(ops);
        content.addView(top);

        if (history.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(label("还没有历史。计算或保存后会出现在这里。", 16, MUTED, false));
            content.addView(empty);
            return;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            String item = history.get(i);
            TextView row = label(item, 16, TEXT, false);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(round(PANEL, dp(12), Color.rgb(38, 42, 52)));
            row.setOnClickListener(v -> copyText(item));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.setMargins(0, dp(8), 0, 0);
            content.addView(row, rp);
        }
    }

    private void showTools() {
        captureDraft();
        content.removeAllViews();
        content.addView(toolRmb());
        content.addView(toolCurrency());
        content.addView(toolBase());
        content.addView(toolFraction());
        content.addView(toolUnits());
        content.addView(toolTime());
    }

    private View toolRmb() {
        LinearLayout c = card();
        c.addView(label("人民币大写", 18, TEXT, true));
        EditText amount = input("输入金额，例如 523458.26");
        TextView out = label("等待转换", 16, GOLD, true);
        c.addView(amount);
        c.addView(actionButton("转换大写金额", () -> {
            try {
                out.setText(rmb(Double.parseDouble(amount.getText().toString())));
            } catch (Exception e) {
                out.setText("请输入数字金额");
            }
        }));
        c.addView(out);
        return c;
    }

    private View toolBase() {
        LinearLayout c = card();
        c.addView(label("进制转换", 18, TEXT, true));
        EditText input = input("输入十进制整数，例如 255");
        TextView out = label("二进制 / 八进制 / 十六进制", 16, GOLD, true);
        c.addView(input);
        c.addView(actionButton("转换", () -> {
            try {
                BigInteger n = new BigInteger(input.getText().toString().trim());
                out.setText("BIN " + n.toString(2) + "\nOCT " + n.toString(8) + "\nHEX " + n.toString(16).toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                out.setText("请输入整数");
            }
        }));
        c.addView(out);
        return c;
    }

    private View toolCurrency() {
        LinearLayout c = card();
        c.addView(label("汇率估算", 18, TEXT, true));
        c.addView(label("离线参考汇率，适合快速估算。正式报销请以银行或支付平台实际汇率为准。", 13, MUTED, false));
        EditText input = input("输入人民币金额，例如 1000");
        TextView out = label("美元 / 欧元 / 卢布 / 日元", 16, GOLD, true);
        c.addView(input);
        c.addView(actionButton("估算", () -> {
            try {
                double cny = Double.parseDouble(input.getText().toString());
                out.setText(
                        "USD $" + df.format(cny / 7.25) + "\n" +
                        "EUR €" + df.format(cny / 7.85) + "\n" +
                        "RUB ₽" + df.format(cny * 11.1) + "\n" +
                        "JPY ¥" + df.format(cny * 21.5)
                );
            } catch (Exception e) {
                out.setText("请输入人民币金额");
            }
        }));
        c.addView(out);
        return c;
    }

    private View toolFraction() {
        LinearLayout c = card();
        c.addView(label("小数 / 分数", 18, TEXT, true));
        EditText input = input("输入小数，例如 0.125");
        TextView out = label("等待转换", 16, GOLD, true);
        c.addView(input);
        c.addView(actionButton("转为分数", () -> {
            try {
                out.setText(toFraction(new BigDecimal(input.getText().toString().trim())));
            } catch (Exception e) {
                out.setText("请输入小数");
            }
        }));
        c.addView(out);
        return c;
    }

    private View toolUnits() {
        LinearLayout c = card();
        c.addView(label("常用单位换算", 18, TEXT, true));
        EditText input = input("输入数值，例如 12.5");
        TextView out = label("米/公里、公斤/斤、平方米/亩", 16, GOLD, true);
        c.addView(input);
        c.addView(actionButton("换算", () -> {
            try {
                double v = Double.parseDouble(input.getText().toString());
                out.setText(
                        "长度：" + df.format(v) + " m = " + df.format(v / 1000) + " km\n" +
                        "重量：" + df.format(v) + " kg = " + df.format(v * 2) + " 斤\n" +
                        "面积：" + df.format(v) + " m² = " + df.format(v / 666.6667) + " 亩\n" +
                        "体积：" + df.format(v) + " L = " + df.format(v / 1000) + " m³"
                );
            } catch (Exception e) {
                out.setText("请输入数值");
            }
        }));
        c.addView(out);
        return c;
    }

    private View toolTime() {
        LinearLayout c = card();
        c.addView(label("时间换算", 18, TEXT, true));
        EditText input = input("输入分钟数，例如 135");
        TextView out = label("小时 / 天 / 秒", 16, GOLD, true);
        c.addView(input);
        c.addView(actionButton("换算", () -> {
            try {
                double minutes = Double.parseDouble(input.getText().toString());
                out.setText(
                        df.format(minutes) + " 分钟\n" +
                        "= " + df.format(minutes / 60) + " 小时\n" +
                        "= " + df.format(minutes / 1440) + " 天\n" +
                        "= " + df.format(minutes * 60) + " 秒"
                );
            } catch (Exception e) {
                out.setText("请输入分钟数");
            }
        }));
        c.addView(out);
        return c;
    }

    private void showAbout() {
        captureDraft();
        content.removeAllViews();
        LinearLayout c = card();
        TextView title = label("风算", 32, GOLD, true);
        title.setGravity(Gravity.CENTER);
        c.addView(title);
        TextView slogan = label("一款懂你的计算管家", 20, TEXT, true);
        slogan.setGravity(Gravity.CENTER);
        c.addView(slogan);
        TextView body = label(
                "\n设计与制作：风乱吹\n\n" +
                "风算是一款黑色高级风格的计算草稿本。它把算式、备注、历史和常用转换放在一起，适合记账、报销、作业检查、日常估算和连续计算。\n\n" +
                "当前版本：1.0.11\n" +
                "功能：备注计算、历史保存、结果求和、可编辑分享、人民币大写、汇率估算、进制转换、小数分数转换、常用单位换算、时间换算。",
                16, MUTED, false);
        c.addView(body);
        content.addView(c);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setBackground(round(PANEL, dp(18), Color.rgb(38, 42, 52)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, dp(8), 0, dp(10));
        c.setLayoutParams(cp);
        return c;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setLineSpacing(dp(2), 1.05f);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.rgb(98, 104, 116));
        e.setTextSize(18);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setHint(hint);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(round(Color.rgb(11, 13, 18), dp(12), Color.rgb(42, 47, 58)));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(52));
        ep.setMargins(0, dp(10), 0, dp(8));
        e.setLayoutParams(ep);
        return e;
    }

    private Button actionButton(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setBackground(round(Color.rgb(38, 42, 52), dp(12), Color.rgb(54, 59, 70)));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private void copyText(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("风算", text));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    private void addHistory(String text) {
        String item = text.trim();
        if (item.isEmpty()) return;
        history.add(item);
        while (history.size() > 80) history.remove(0);
        persistHistory();
    }

    private void loadHistory() {
        history.clear();
        String raw = prefs.getString("history", "");
        if (!raw.isEmpty()) {
            String[] parts = raw.split("\\n---FS---\\n");
            for (String p : parts) if (!p.trim().isEmpty()) history.add(p);
        }
    }

    private void persistHistory() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) sb.append("\n---FS---\n");
            sb.append(history.get(i));
        }
        prefs.edit().putString("history", sb.toString()).apply();
    }

    private double sumHistory() {
        double sum = 0;
        for (String h : history) {
            int eq = h.lastIndexOf('=');
            if (eq >= 0) {
                try {
                    sum += Double.parseDouble(h.substring(eq + 1).trim());
                } catch (Exception ignored) {
                }
            }
        }
        return sum;
    }

    private String lastAnswer() {
        for (int i = history.size() - 1; i >= 0; i--) {
            int eq = history.get(i).lastIndexOf('=');
            if (eq >= 0) return history.get(i).substring(eq + 1).trim();
        }
        return "0";
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private String toFraction(BigDecimal decimal) {
        BigDecimal stripped = decimal.stripTrailingZeros();
        int scale = Math.max(0, stripped.scale());
        BigInteger denominator = BigInteger.TEN.pow(scale);
        BigInteger numerator = stripped.movePointRight(scale).toBigIntegerExact();
        BigInteger gcd = numerator.gcd(denominator);
        return numerator.divide(gcd) + " / " + denominator.divide(gcd);
    }

    private String rmb(double value) {
        String[] cnNums = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
        String unitSeq = "仟佰拾亿仟佰拾万仟佰拾元角分";
        BigDecimal bd = BigDecimal.valueOf(Math.abs(value)).setScale(2, RoundingMode.HALF_UP);
        BigInteger cents = bd.movePointRight(2).toBigIntegerExact();
        if (cents.equals(BigInteger.ZERO)) return "零元整";
        String number = cents.toString();
        if (number.length() > unitSeq.length()) return "金额过大";
        StringBuilder result = new StringBuilder();
        String units = unitSeq.substring(unitSeq.length() - number.length());
        for (int i = 0; i < number.length(); i++) {
            int n = number.charAt(i) - '0';
            result.append(cnNums[n]).append(units.charAt(i));
        }
        String text = result.toString()
                .replaceAll("零[仟佰拾]", "零")
                .replaceAll("零+", "零")
                .replaceAll("零亿", "亿")
                .replaceAll("零万", "万")
                .replaceAll("亿万", "亿")
                .replaceAll("零元", "元")
                .replaceAll("零角零分$", "整")
                .replaceAll("零分$", "整")
                .replaceAll("零角", "零")
                .replaceAll("^元", "零元");
        return value < 0 ? "负" + text : text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int keyFill(String key) {
        if ("AC".equals(key)) return Color.rgb(221, 49, 55);
        if ("⌫".equals(key)) return Color.rgb(255, 145, 56);
        if (isLightKey(key)) return Color.rgb(241, 241, 239);
        if ("帮助".equals(key) || "函数".equals(key) || "常量".equals(key) ||
                "常用".equals(key) || isSoundKey(key)) {
            return Color.rgb(81, 83, 82);
        }
        return Color.rgb(126, 128, 126);
    }

    private int keyTextColor(String key) {
        if ("=".equals(key)) return Color.rgb(185, 0, 12);
        if ("AC".equals(key) || "⌫".equals(key)) return Color.WHITE;
        return isLightKey(key) ? Color.rgb(42, 43, 44) : Color.WHITE;
    }

    private boolean isLightKey(String key) {
        return key.matches("[0-9.]") || "=".equals(key) || "文".equals(key);
    }

    private int adjustColor(int color, int amount) {
        return Color.rgb(
                Math.max(0, Math.min(255, Color.red(color) + amount)),
                Math.max(0, Math.min(255, Color.green(color) + amount)),
                Math.max(0, Math.min(255, Color.blue(color) + amount)));
    }

    private int mixColor(int from, int to, float amount) {
        float keep = 1f - amount;
        return Color.rgb(
                Math.round(Color.red(from) * keep + Color.red(to) * amount),
                Math.round(Color.green(from) * keep + Color.green(to) * amount),
                Math.round(Color.blue(from) * keep + Color.blue(to) * amount));
    }

    private void drawKeyCap(Canvas canvas, RectF bounds, int baseColor, float radius, boolean pressed) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean light = isLightColor(baseColor);
        boolean orange = Color.red(baseColor) > 235 && Color.green(baseColor) > 95 && Color.green(baseColor) < 180;
        boolean red = Color.red(baseColor) > 180 && Color.green(baseColor) < 80;
        float lift = pressed ? dp(1.3f) : 0f;
        RectF slot = new RectF(bounds);
        slot.inset(dp(0.2f), dp(0.2f));
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(33, 35, 35));
        canvas.drawRoundRect(slot, radius + dp(1), radius + dp(1), p);

        RectF shadow = new RectF(bounds.left + dp(1.4f), bounds.top + dp(3.8f),
                bounds.right - dp(1.4f), bounds.bottom - dp(0.2f));
        p.setColor(Color.argb(112, 0, 0, 0));
        canvas.drawRoundRect(shadow, radius, radius, p);

        RectF key = new RectF(bounds.left + dp(0.9f), bounds.top + dp(0.4f) + lift,
                bounds.right - dp(0.9f), bounds.bottom - dp(2.7f) + lift);
        int top = adjustColor(baseColor, light ? 18 : 15);
        int mid = baseColor;
        int bottom = adjustColor(baseColor, light ? -16 : -24);
        if (orange) {
            top = Color.rgb(255, 160, 72);
            mid = Color.rgb(255, 145, 56);
            bottom = Color.rgb(231, 112, 24);
        } else if (red) {
            top = Color.rgb(232, 64, 70);
            mid = Color.rgb(221, 49, 55);
            bottom = Color.rgb(194, 36, 43);
        }
        p.setShader(new LinearGradient(0, key.top, 0, key.bottom,
                new int[]{top, mid, bottom},
                new float[]{0f, 0.56f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(key, radius, radius, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(1.1f));
        p.setColor(light ? Color.rgb(224, 224, 222) :
                (orange ? Color.rgb(255, 169, 83) :
                        (red ? Color.rgb(232, 70, 76) : Color.rgb(135, 137, 136))));
        canvas.drawRoundRect(key, radius, radius, p);

        p.setStrokeWidth(dp(1));
        p.setColor(light ? Color.argb(160, 255, 255, 255) : Color.argb(84, 255, 255, 255));
        canvas.drawLine(key.left + radius, key.top + dp(1.5f),
                key.right - radius, key.top + dp(1.5f), p);

        p.setColor(Color.argb(light ? 58 : 92, 0, 0, 0));
        canvas.drawLine(key.left + radius, key.bottom - dp(1.1f),
                key.right - radius, key.bottom - dp(1.1f), p);
    }
    private boolean isLightColor(int color) {
        return Color.red(color) + Color.green(color) + Color.blue(color) > 560;
    }

    private class KeyCapDrawable extends Drawable {
        private final int color;
        private final float radius;
        private final boolean light;
        private boolean pressed;

        KeyCapDrawable(int color, float radius, boolean light) {
            this.color = color;
            this.radius = radius;
            this.light = light;
        }

        @Override
        public void draw(Canvas canvas) {
            RectF b = new RectF(getBounds());
            drawKeyCap(canvas, b, color, radius, pressed);
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean nowPressed = false;
            for (int s : state) {
                if (s == android.R.attr.state_pressed) {
                    nowPressed = true;
                    break;
                }
            }
            if (pressed != nowPressed) {
                pressed = nowPressed;
                invalidateSelf();
                return true;
            }
            return false;
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private class CalculatorShellDrawable extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        public void draw(Canvas canvas) {
            RectF b = new RectF(getBounds());
            float r = dp(22);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(125, 0, 0, 0));
            canvas.drawRoundRect(new RectF(b.left + dp(2), b.top + dp(4), b.right - dp(1), b.bottom + dp(3)), r, r, p);
            p.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                    new int[]{Color.rgb(252, 252, 250), Color.rgb(232, 232, 229), Color.rgb(246, 246, 244)},
                    new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(b, r, r, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(Color.rgb(178, 180, 178));
            canvas.drawRoundRect(new RectF(b.left + dp(2), b.top + dp(2), b.right - dp(2), b.bottom - dp(2)), r - dp(2), r - dp(2), p);
            p.setStrokeWidth(dp(1));
            p.setColor(Color.argb(180, 255, 255, 255));
            canvas.drawRoundRect(new RectF(b.left + dp(6), b.top + dp(5), b.right - dp(6), b.bottom - dp(8)), r - dp(6), r - dp(6), p);
        }

        @Override
        public void setAlpha(int alpha) {
            p.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            p.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private class PaperFrameDrawable extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        public void draw(Canvas canvas) {
            RectF b = new RectF(getBounds());
            float r = dp(15);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(90, 0, 0, 0));
            canvas.drawRoundRect(new RectF(b.left + dp(1), b.top + dp(3), b.right - dp(1), b.bottom + dp(4)), r, r, p);
            p.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                    new int[]{Color.rgb(255, 255, 254), Color.rgb(236, 236, 233), Color.rgb(249, 249, 247)},
                    new float[]{0f, 0.70f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(b, r, r, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.2f));
            p.setColor(Color.rgb(180, 181, 178));
            canvas.drawRoundRect(new RectF(b.left + dp(2), b.top + dp(2), b.right - dp(2), b.bottom - dp(2)),
                    r - dp(2), r - dp(2), p);
        }

        @Override
        public void setAlpha(int alpha) {
            p.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            p.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private class IconKeyView extends View {
        private final String key;
        private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconKeyView(Context context, String key) {
            super(context);
            this.key = key;
            setWillNotDraw(false);
            setClickable(true);
            setFocusable(true);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setContentDescription(key);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int base = keyFill(key);
            drawKeyCap(canvas, new RectF(0, 0, getWidth(), getHeight()), base, dp(6), isPressed());
            icon.clearShadowLayer();
            icon.setStyle(Paint.Style.FILL);
            icon.setColor(keyTextColor(key));
            icon.setTextAlign(Paint.Align.CENTER);
            icon.setTypeface(Typeface.DEFAULT_BOLD);
            icon.setTextSize(dp(iconTextSize(key)));
            Paint.FontMetrics fm = icon.getFontMetrics();
            float y = getHeight() * 0.48f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(iconText(key), getWidth() / 2f, y, icon);
        }

        private String iconText(String key) {
            if ("布局".equals(key)) return "▣";
            if ("复".equals(key)) return "复制";
            if ("贴".equals(key)) return "粘贴";
            if ("↵".equals(key)) return "↵";
            return key;
        }

        private int iconTextSize(String key) {
            if ("复".equals(key) || "贴".equals(key)) return 18;
            if ("布局".equals(key)) return 28;
            if ("↵".equals(key)) return 34;
            return 30;
        }
    }

    private class LayoutPreviewView extends View {
        private final int mode;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        LayoutPreviewView(Context context, int mode) {
            super(context);
            this.mode = mode;
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(26, 28, 32));
            canvas.drawRoundRect(new RectF(0, 0, w, h), dp(4), dp(4), p);
            p.setColor(Color.rgb(239, 239, 239));
            canvas.drawRoundRect(new RectF(dp(4), dp(4), w - dp(4), h * 0.43f), dp(3), dp(3), p);
            p.setColor(Color.rgb(210, 210, 210));
            p.setStrokeWidth(dp(1));
            for (float y = dp(16); y < h * 0.40f; y += dp(13)) {
                canvas.drawLine(dp(7), y, w - dp(7), y, p);
            }

            int rows;
            int cols = mode == 5 ? 4 : 5;
            if (mode == 0) rows = 8;
            else if (mode == 1 || mode == 2) rows = 7;
            else if (mode == 3) rows = 6;
            else rows = 5;
            float gap = dp(2);
            float top = h * 0.45f;
            float cellW = (w - gap * (cols + 1)) / cols;
            float cellH = (h - top - gap * (rows + 1)) / rows;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    boolean number = r >= Math.max(1, rows - 4) && c < Math.min(3, cols);
                    boolean danger = r == 0 && c == cols - 1;
                    boolean warn = (mode == 4 || mode == 5) && r == 0 && c == cols - 2;
                    p.setColor(danger ? Color.rgb(220, 47, 55) :
                            (warn ? Color.rgb(255, 146, 57) :
                                    (number ? Color.rgb(241, 241, 239) : Color.rgb(126, 128, 126))));
                    float x = gap + c * (cellW + gap);
                    float y = top + gap + r * (cellH + gap);
                    canvas.drawRoundRect(new RectF(x, y, x + cellW, y + cellH), dp(2), dp(2), p);
                }
            }
            if (mode == layoutMode) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(2));
                p.setColor(GOLD);
                canvas.drawRoundRect(new RectF(dp(1), dp(1), w - dp(1), h - dp(1)), dp(5), dp(5), p);
            }
        }
    }

    private class SoundKeyView extends View {
        private final String state;
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SoundKeyView(Context context, String state) {
            super(context);
            this.state = state;
            setWillNotDraw(false);
            setClickable(true);
            setFocusable(true);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setContentDescription("声音");
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int base = keyFill(state);
            drawKeyCap(canvas, new RectF(0, 0, getWidth(), getHeight()), base, dp(6), isPressed());
            float size = Math.min(getWidth(), getHeight()) * 0.58f;
            float cx = getWidth() / 2f;
            float cy = getHeight() * 0.47f;
            RectF box = new RectF(cx - size * 0.5f, cy - size * 0.46f,
                    cx + size * 0.5f, cy + size * 0.46f);
            if ("女声".equals(state) && femaleVoiceBitmap != null) {
                drawFemaleVoiceBitmap(canvas, box);
            } else {
                drawSpeakerIcon(canvas, box, "静音".equals(state));
            }
        }

        private void drawFemaleVoiceBitmap(Canvas canvas, RectF box) {
            float ratio = femaleVoiceBitmap.getWidth() / (float) femaleVoiceBitmap.getHeight();
            RectF dst = new RectF(box);
            if (dst.width() / dst.height() > ratio) {
                float w = dst.height() * ratio;
                dst.left = dst.centerX() - w / 2f;
                dst.right = dst.centerX() + w / 2f;
            } else {
                float h = dst.width() / ratio;
                dst.top = dst.centerY() - h / 2f;
                dst.bottom = dst.centerY() + h / 2f;
            }
            dst.inset(-dp(1.5f), -dp(1.5f));
            iconPaint.reset();
            iconPaint.setAntiAlias(true);
            iconPaint.setFilterBitmap(true);
            iconPaint.setDither(true);
            iconPaint.setAlpha(236);
            canvas.drawBitmap(femaleVoiceBitmap,
                    new Rect(0, 0, femaleVoiceBitmap.getWidth(), femaleVoiceBitmap.getHeight()), dst, iconPaint);
        }

        private void drawSpeakerIcon(Canvas canvas, RectF box, boolean muted) {
            int color = keyTextColor(state);
            iconPaint.reset();
            iconPaint.setAntiAlias(true);
            iconPaint.setStyle(Paint.Style.FILL);
            iconPaint.setColor(color);
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
            iconPaint.setStrokeJoin(Paint.Join.ROUND);

            float w = box.width();
            float h = box.height();
            float left = box.left + w * 0.08f;
            float midY = box.centerY();
            RectF chamber = new RectF(left, midY - h * 0.18f, left + w * 0.17f, midY + h * 0.18f);
            canvas.drawRoundRect(chamber, dp(2.5f), dp(2.5f), iconPaint);

            Path horn = new Path();
            horn.moveTo(left + w * 0.18f, midY - h * 0.22f);
            horn.lineTo(left + w * 0.43f, midY - h * 0.40f);
            horn.lineTo(left + w * 0.43f, midY + h * 0.40f);
            horn.lineTo(left + w * 0.18f, midY + h * 0.22f);
            horn.close();
            canvas.drawPath(horn, iconPaint);

            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeWidth(dp(3.2f));
            iconPaint.setColor(color);
            if (!muted) {
                RectF wave1 = new RectF(box.left + w * 0.45f, box.top + h * 0.22f,
                        box.left + w * 0.70f, box.bottom - h * 0.22f);
                RectF wave2 = new RectF(box.left + w * 0.52f, box.top + h * 0.07f,
                        box.left + w * 0.93f, box.bottom - h * 0.07f);
                canvas.drawArc(wave1, -42, 84, false, iconPaint);
                canvas.drawArc(wave2, -43, 86, false, iconPaint);
            } else {
                iconPaint.setStrokeWidth(dp(5.2f));
                canvas.drawLine(box.left + w * 0.55f, box.top + h * 0.18f,
                        box.right - w * 0.03f, box.bottom - h * 0.18f, iconPaint);
            }
        }
    }
    private class PaperDrawable extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        PaperDrawable() {
            fill.setColor(Color.rgb(239, 239, 239));
            line.setColor(Color.rgb(210, 211, 209));
            line.setStrokeWidth(dp(1));
            stroke.setColor(Color.rgb(174, 176, 174));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1.3f));
        }

        @Override
        public void draw(Canvas canvas) {
            float radius = dp(10);
            RectF b = new RectF(getBounds());
            fill.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                    new int[]{Color.rgb(249, 249, 248), Color.rgb(235, 235, 233), Color.rgb(247, 247, 246)},
                    new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(b, radius, radius, fill);
            fill.setShader(null);
            canvas.drawRoundRect(b.left + 1, b.top + 1, b.right - 1, b.bottom - 1, radius, radius, stroke);
            Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
            inner.setStyle(Paint.Style.STROKE);
            inner.setStrokeWidth(dp(2));
            inner.setColor(Color.argb(70, 0, 0, 0));
            canvas.drawRoundRect(b.left + dp(3), b.top + dp(3), b.right - dp(3), b.bottom - dp(3),
                    radius - dp(3), radius - dp(3), inner);
            inner.setStrokeWidth(dp(1));
            inner.setColor(Color.argb(145, 255, 255, 255));
            canvas.drawRoundRect(b.left + dp(5), b.top + dp(4), b.right - dp(5), b.bottom - dp(6),
                    radius - dp(5), radius - dp(5), inner);
            float rowHeight = editorLineHeightPx();
            float y = getBounds().top + (editor == null ? dp(12) : editor.getTotalPaddingTop()) + rowHeight;
            float bottom = getBounds().bottom - (editor == null ? dp(10) : editor.getTotalPaddingBottom());
            while (y < bottom) {
                canvas.drawLine(getBounds().left + dp(12), y, getBounds().right - dp(12), y, line);
                y += rowHeight;
            }
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            line.setAlpha(alpha);
            stroke.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fill.setColorFilter(colorFilter);
            line.setColorFilter(colorFilter);
            stroke.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    private static class Parser {
        private final String s;
        private int pos = -1;
        private int ch;

        Parser(String s) {
            this.s = s;
        }

        double parse() {
            next();
            double x = expression();
            if (pos < s.length()) throw new RuntimeException("unexpected");
            return x;
        }

        private void next() {
            ch = (++pos < s.length()) ? s.charAt(pos) : -1;
        }

        private boolean eat(int c) {
            while (ch == ' ') next();
            if (ch == c) {
                next();
                return true;
            }
            return false;
        }

        private double expression() {
            double x = term();
            for (;;) {
                if (eat('+')) x += term();
                else if (eat('-')) x -= term();
                else return x;
            }
        }

        private double term() {
            double x = factor();
            for (;;) {
                if (eat('*')) x *= factor();
                else if (eat('/')) x /= factor();
                else return x;
            }
        }

        private double factor() {
            if (eat('+')) return factor();
            if (eat('-')) return -factor();

            double x;
            int start = pos;
            if (eat('(')) {
                x = expression();
                if (!eat(')')) throw new RuntimeException("missing )");
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') next();
                x = Double.parseDouble(s.substring(start, pos));
            } else if (Character.isLetter(ch)) {
                while (Character.isLetter(ch)) next();
                String name = s.substring(start, pos).toLowerCase(Locale.ROOT);
                if ("pi".equals(name)) x = Math.PI;
                else if ("e".equals(name)) x = Math.E;
                else {
                    x = factor();
                    switch (name) {
                        case "sqrt": x = Math.sqrt(x); break;
                        case "sin": x = Math.sin(Math.toRadians(x)); break;
                        case "cos": x = Math.cos(Math.toRadians(x)); break;
                        case "tan": x = Math.tan(Math.toRadians(x)); break;
                        case "ln": x = Math.log(x); break;
                        case "lg":
                        case "log": x = Math.log10(x); break;
                        default: throw new RuntimeException("function");
                    }
                }
            } else {
                throw new RuntimeException("number");
            }

            if (eat('%')) x = x / 100.0;
            if (eat('^')) x = Math.pow(x, factor());
            return x;
        }
    }
}






