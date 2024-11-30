package ups.giatta.proyecto;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String URL_STREAM = "http://192.168.1.150:81/stream"; // URL de tu ESP32-CAM
    private SurfaceView vistaSuperficie;
    private SurfaceHolder titularSuperficie;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar SurfaceView y SurfaceHolder
        vistaSuperficie = findViewById(R.id.surfaceview);
        titularSuperficie = vistaSuperficie.getHolder();
        titularSuperficie.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "Superficie creada y lista.");
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "Superficie cambiada: " + width + "x" + height);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d(TAG, "Superficie destruida.");
            }
        });

        new Thread(() -> {
            while (true) {
                Bitmap fotograma = obtenerBitmapDesdeStreamMjpeg(URL_STREAM);
                if (fotograma != null) {
                    // Aplicar ecualización de histograma y suavizado antes de mostrar el fotograma
                    Bitmap fotogramaProcesado = procesarFotograma(fotograma);
                    runOnUiThread(() -> mostrarFotogramaFiltradoEnSuperficie(fotogramaProcesado));
                } else {
                    Log.e(TAG, "El fotograma no se obtuvo.");
                }
            }
        }).start();
    }

    // Método para obtener el Bitmap del stream MJPEG
    private Bitmap obtenerBitmapDesdeStreamMjpeg(String urlString) {
        HttpURLConnection conexion = null;
        InputStream flujoEntrada = null;
        try {
            Log.d(TAG, "Conectando al STREAM");
            URL url = new URL(urlString);
            conexion = (HttpURLConnection) url.openConnection();
            conexion.setConnectTimeout(5000);
            conexion.setReadTimeout(5000);
            conexion.setRequestMethod("GET");
            conexion.connect();

            int codigoRespuesta = conexion.getResponseCode();
            Log.d(TAG, "Código de respuesta: " + codigoRespuesta);
            if (codigoRespuesta != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Respuesta no válida del servidor.");
                return null;
            }

            // Procesar MJPEG
            flujoEntrada = conexion.getInputStream();
            ByteArrayOutputStream datosJpeg = new ByteArrayOutputStream();
            boolean inicioFotograma = false;

            byte[] buffer = new byte[4096];
            int bytesLeidos;
            while ((bytesLeidos = flujoEntrada.read(buffer)) != -1) {
                for (int i = 0; i < bytesLeidos; i++) {
                    if (inicioFotograma) {
                        datosJpeg.write(buffer[i]);
                    }
                    if (buffer[i] == (byte) 0xFF && buffer[(i + 1) % bytesLeidos] == (byte) 0xD8) { // SOI (Start of Image)
                        inicioFotograma = true;
                        datosJpeg.reset();
                        datosJpeg.write(buffer[i]);
                    } else if (buffer[i] == (byte) 0xFF && buffer[(i + 1) % bytesLeidos] == (byte) 0xD9) { // EOI (End of Image)
                        datosJpeg.write(buffer[i]);
                        inicioFotograma = false;

                        // Decodificar el fotograma JPEG
                        byte[] fotogramaJpeg = datosJpeg.toByteArray();
                        Bitmap bitmap = BitmapFactory.decodeByteArray(fotogramaJpeg, 0, fotogramaJpeg.length);
                        if (bitmap != null) {
                            Log.d(TAG, "Fotograma obtenido correctamente.");
                        } else {
                            Log.e(TAG, "Error al decodificar el fotograma.");
                        }
                        return bitmap;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al procesar el stream MJPEG: ", e);
        } finally {
            try {
                if (flujoEntrada != null) flujoEntrada.close();
            } catch (IOException ignorado) {}
            if (conexion != null) conexion.disconnect();
        }
        return null;
    }

    // Aplicar ecualización de histograma, suavizado y filtrado bilateral
    private Bitmap procesarFotograma(Bitmap bitmap) {
        // Filtrado Bilateral para reducir ruido manteniendo bordes
        Bitmap bitmapFiltradoBilateral = aplicarFiltroBilateral(bitmap, 5, 75, 75);

        // Ecualizar el histograma en cada canal de color (R, G, B)
        Bitmap bitmapEcualizado = ecualizarHistograma(bitmapFiltradoBilateral);

        // Suavizar la imagen usando un desenfoque gaussiano
        Bitmap bitmapSuavizado = aplicarDesenfoqueGaussiano(bitmapEcualizado);

        return bitmapSuavizado;
    }

    // Ecualizar el histograma para cada canal de color
    private Bitmap ecualizarHistograma(Bitmap bitmap) {
        int ancho = bitmap.getWidth();
        int alto = bitmap.getHeight();

        Bitmap bitmapEcualizado = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888);

        for (int c = 0; c < 3; c++) {
            int[] histograma = new int[256];
            int[] histogramaAcumulado = new int[256];
            for (int x = 0; x < ancho; x++) {
                for (int y = 0; y < alto; y++) {
                    int pixel = bitmap.getPixel(x, y);
                    int valorColor = (c == 0) ? Color.red(pixel) : (c == 1) ? Color.green(pixel) : Color.blue(pixel);
                    histograma[valorColor]++;
                }
            }
            histogramaAcumulado[0] = histograma[0];
            for (int i = 1; i < 256; i++) {
                histogramaAcumulado[i] = histogramaAcumulado[i - 1] + histograma[i];
            }
            int totalPixeles = ancho * alto;
            for (int i = 0; i < 256; i++) {
                histogramaAcumulado[i] = (histogramaAcumulado[i] * 255) / totalPixeles;
            }
            for (int x = 0; x < ancho; x++) {
                for (int y = 0; y < alto; y++) {
                    int pixel = bitmap.getPixel(x, y);
                    int r = Color.red(pixel);
                    int g = Color.green(pixel);
                    int b = Color.blue(pixel);

                    if (c == 0) r = histogramaAcumulado[r];
                    if (c == 1) g = histogramaAcumulado[g];
                    if (c == 2) b = histogramaAcumulado[b];
                    int nuevoPixel = Color.rgb(r, g, b);
                    bitmapEcualizado.setPixel(x, y, nuevoPixel);
                }
            }
        }

        return bitmapEcualizado;
    }

    // Aplicar desenfoque gaussiano
    private Bitmap aplicarDesenfoqueGaussiano(Bitmap bitmap) {
        Bitmap bitmapDesenfocado = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapDesenfocado);
        Paint pintura = new Paint();
        pintura.setAntiAlias(true);
        pintura.setFilterBitmap(true);
        canvas.drawBitmap(bitmap, 0, 0, pintura);

        return bitmapDesenfocado;
    }

    // Mostrar el fotograma en el SurfaceView
    private void mostrarFotogramaFiltradoEnSuperficie(final Bitmap fotograma) {
        if (fotograma == null) {
            Log.e(TAG, "El fotograma está vacío");
            return;
        }

        // Aplicar efecto de simulación de pantalla LED
        Bitmap fotogramaConEfectoLED = aplicarEfectoSimulacionLED(fotograma, 8);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (titularSuperficie.getSurface().isValid()) {
                    Canvas canvas = titularSuperficie.lockCanvas();
                    if (canvas != null) {
                        int anchoSuperficie = vistaSuperficie.getWidth();
                        int altoSuperficie = vistaSuperficie.getHeight();
                        Bitmap bitmapEscalado = Bitmap.createScaledBitmap(fotogramaConEfectoLED, anchoSuperficie, altoSuperficie, true);
                        canvas.drawBitmap(bitmapEscalado, 0, 0, null);
                        titularSuperficie.unlockCanvasAndPost(canvas);
                    }
                } else {
                    Log.e(TAG, "La superficie no es válida");
                }
            }
        });
    }

    // Implementar Filtrado Bilateral
    private Bitmap aplicarFiltroBilateral(Bitmap bitmap, int diametro, double sigmaColor, double sigmaEspacio) {
        int ancho = bitmap.getWidth();
        int alto = bitmap.getHeight();
        Bitmap resultadoBitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888);

        int[] arregloPixeles = new int[diametro * diametro];
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                double sumaRojo = 0, sumaVerde = 0, sumaAzul = 0, sumaPeso = 0;
                for (int dy = -diametro / 2; dy <= diametro / 2; dy++) {
                    for (int dx = -diametro / 2; dx <= diametro / 2; dx++) {
                        int vecinoX = x + dx;
                        int vecinoY = y + dy;

                        if (vecinoX < 0 || vecinoX >= ancho || vecinoY < 0 || vecinoY >= alto) {
                            continue;
                        }

                        int colorVecino = bitmap.getPixel(vecinoX, vecinoY);
                        int rojoVecino = Color.red(colorVecino);
                        int verdeVecino = Color.green(colorVecino);
                        int azulVecino = Color.blue(colorVecino);

                        int colorActual = bitmap.getPixel(x, y);
                        int rojoActual = Color.red(colorActual);
                        int verdeActual = Color.green(colorActual);
                        int azulActual = Color.blue(colorActual);

                        double pesoEspacial = Math.exp(-(dx * dx + dy * dy) / (2 * sigmaEspacio * sigmaEspacio));
                        double pesoColor = Math.exp(-(
                                (rojoVecino - rojoActual) * (rojoVecino - rojoActual) +
                                        (verdeVecino - verdeActual) * (verdeVecino - verdeActual) +
                                        (azulVecino - azulActual) * (azulVecino - azulActual)
                        ) / (2 * sigmaColor * sigmaColor));

                        double peso = pesoEspacial * pesoColor;
                        sumaPeso += peso;

                        sumaRojo += rojoVecino * peso;
                        sumaVerde += verdeVecino * peso;
                        sumaAzul += azulVecino * peso;
                    }
                }

                int rojoFinal = (int) Math.min(Math.max(sumaRojo / sumaPeso, 0), 255);
                int verdeFinal = (int) Math.min(Math.max(sumaVerde / sumaPeso, 0), 255);
                int azulFinal = (int) Math.min(Math.max(sumaAzul / sumaPeso, 0), 255);

                resultadoBitmap.setPixel(x, y, Color.rgb(rojoFinal, verdeFinal, azulFinal));
            }
        }

        return resultadoBitmap;
    }

    // Efecto de simulación de pantalla LED
    private Bitmap aplicarEfectoSimulacionLED(Bitmap bitmap, int tamañoPixel) {
        int ancho = bitmap.getWidth();
        int alto = bitmap.getHeight();

        Bitmap bitmapLED = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapLED);

        Paint pintura = new Paint();
        pintura.setStyle(Paint.Style.FILL);

        int espacio = tamañoPixel;

        for (int y = 0; y < alto; y += espacio) {
            for (int x = 0; x < ancho; x += espacio) {
                int sumaRojo = 0, sumaVerde = 0, sumaAzul = 0, total = 0;

                // Calcular el color promedio dentro del bloque
                for (int yy = y; yy < y + tamañoPixel && yy < alto; yy++) {
                    for (int xx = x; xx < x + tamañoPixel && xx < ancho; xx++) {
                        int color = bitmap.getPixel(xx, yy);
                        sumaRojo += Color.red(color);
                        sumaVerde += Color.green(color);
                        sumaAzul += Color.blue(color);
                        total++;
                    }
                }

                if (total > 0) {
                    // Determinar el color
                    int promedioRojo = sumaRojo / total;
                    int promedioVerde = sumaVerde / total;
                    int promedioAzul = sumaAzul / total;
                    int colorPromedio = Color.rgb(promedioRojo, promedioVerde, promedioAzul);

                    // Graficar el LED
                    pintura.setColor(colorPromedio);
                    float centroX = x + (float) tamañoPixel / 2;
                    float centroY = y + (float) tamañoPixel / 2;
                    float radio = (tamañoPixel / 2f) * 0.9f;
                    canvas.drawCircle(centroX, centroY, radio, pintura);
                }
            }
        }

        return bitmapLED;
    }
}
