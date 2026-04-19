# Changelog - Uh No Habia

## v1.2.0 (14 Abril 2026)

### Limpieza y optimizacion
- Eliminados archivos muertos: SmartAnalysisScreen, SmartListAnalyzer
- Removidas 3 dependencias no usadas: firebase-functions, play-services-location, splashscreen
- Limpiado version catalog (4 entries huerfanas removidas)
- Agregadas reglas de ProGuard para Firebase, Room, OkHttp, ML Kit, Hilt, Compose
- Removidos imports muertos en ShoppingListDetailScreen y ShoppingListsScreen
- Removida ruta SmartAnalysis (feature no utilizada)

### Nuevas funcionalidades
- Importar lista desde texto: pegar lista de WhatsApp y crear productos automaticamente
- Ahorro acumulado: muestra "Este mes ahorraste X" en resultados de optimizacion
- Registro automatico de ahorros en cada optimizacion (tabla savings_history)
- Sincronizacion de categorias personalizadas entre dispositivos via Firebase
- Modo "en el super": vista simplificada con checkboxes grandes y barra de progreso
- Duplicar lista: copiar una lista existente como template
- Productos sugeridos en lista vacia basados en historial de uso

### Advertencias de exclusividad en optimizacion
- Deteccion de productos no disponibles en la cadena recomendada
- Card de alerta con boton "Volver a cambiar marcas"
- Indicador "Solo en X" en la seleccion de marca

## v1.1.0 (Abril 2026)

### Widget de escritorio
- Arreglado: el widget no mostraba productos (usaba una DB diferente a la app)
- Actividad de configuracion para elegir que lista mostrar
- Contador de productos pendientes/total en el titulo
- Checkmarks visuales por producto

### Rediseno de pantalla principal
- Header con degradado y saludo dinamico
- Fila de acciones rapidas adaptable a cualquier resolucion
- Cards de listas con avatares con gradiente y tags de estado
- Status bar transparente integrada con el degradado

### Emoji y personalizacion de listas
- Selector de emoji al crear lista (24 iconos)
- El emoji elegido se muestra en el avatar
- Campo de color por lista

### Pantalla de detalle de lista
- Swipe-to-dismiss para marcar/eliminar
- Barra de progreso de compras
- Pull-to-refresh
- Haptic feedback
- Chips de categoria con colores distintivos

### Optimizador de compras
- Mejor dia para comprar segun promos bancarias
- Comparacion de costos por dia de la semana
- Ranking de cadenas con ahorro

### Busqueda global
- Buscar productos en todas las listas

### Exportar listas
- Exportar como texto o CSV desde Configuracion

### Backend y sincronizacion
- WorkManager: sync periodico cada 30 min en background
- Firebase Cloud Messaging registrado
- Historial de precios: tabla local para trackear cambios

### Dependencias actualizadas
- Kotlin 2.0.21 a 2.1.20
- Compose BOM 2024.12.01 a 2025.05.00
- Room 2.6.1 a 2.7.1
- Hilt 2.53.1 a 2.56.2
- Lifecycle 2.8.7 a 2.9.0
- Navigation 2.8.5 a 2.9.0
- Firebase BOM 33.7.0 a 33.12.0
- R8 habilitado para release builds
- Transiciones animadas entre pantallas