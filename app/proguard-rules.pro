# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# ==== ROOM / SQLite ====
# Keep Room entity classes and DAO interfaces
-keep class androidx.room.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Required for KSP-generated code (schemas, etc.)
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * {
    @androidx.room.Dao <methods>;
}

# ==== Retrofit / OkHttp ====
# Keep Retrofit interfaces and model classes used with Gson
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class com.squareup.okhttp3.** { *; }
-keep class com.squareup.retrofit2.** { *; }

# Prevent stripping of classes used with @SerializedName
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ==== ML Kit ====
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ==== CameraX ====
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ==== Compose ====
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ==== FileProvider ====
-keep class androidx.core.content.FileProvider { *; }

# ==== PDF / Apache POI ====
-keep class com.itextpdf.** { *; }
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.apache.commons.** { *; }

# ==== Google Play Billing ====
-keep class com.android.billingclient.** { *; }

# ==== AppIntro (onboarding library) ====
-keep class com.github.appintro.** { *; }

# ==== Permissions / Image Picker ====
-keep class com.karumi.dexter.** { *; }
-keep class com.github.dhaval2404.imagepicker.** { *; }

# ==== Prevent obfuscation of model classes ====
-keep class com.mmalyil.smartdocai.model.** { *; }

# ==== Optional: Keep your activities from being renamed (for intents, analytics) ====
-keep public class com.mmalyil.smartdocai.**Activity { *; }

# ==== Keep MainActivity entry point ====
-keep public class com.mmalyil.smartdocai.MainActivity { *; }

# ==== General annotations ====
-keepattributes *Annotation*

# ==== Debugging tip: Retain line numbers ====
-keepattributes SourceFile,LineNumberTable

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn aQute.bnd.annotation.spi.ServiceConsumer
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-dontwarn com.github.javaparser.ParseResult
-dontwarn com.github.javaparser.ParserConfiguration$LanguageLevel
-dontwarn com.github.javaparser.ParserConfiguration
-dontwarn com.github.javaparser.ast.CompilationUnit
-dontwarn com.github.javaparser.ast.Node
-dontwarn com.github.javaparser.ast.NodeList
-dontwarn com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
-dontwarn com.github.javaparser.ast.body.MethodDeclaration
-dontwarn com.github.javaparser.ast.body.Parameter
-dontwarn com.github.javaparser.ast.body.TypeDeclaration
-dontwarn com.github.javaparser.ast.expr.SimpleName
-dontwarn com.github.javaparser.ast.type.PrimitiveType
-dontwarn com.github.javaparser.ast.type.ReferenceType
-dontwarn com.github.javaparser.ast.type.Type
-dontwarn com.github.javaparser.ast.type.TypeParameter
-dontwarn com.github.javaparser.resolution.MethodUsage
-dontwarn com.github.javaparser.resolution.SymbolResolver
-dontwarn com.github.javaparser.resolution.TypeSolver
-dontwarn com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
-dontwarn com.github.javaparser.resolution.declarations.ResolvedParameterDeclaration
-dontwarn com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
-dontwarn com.github.javaparser.resolution.types.ResolvedType
-dontwarn com.github.javaparser.symbolsolver.JavaSymbolSolver
-dontwarn com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver
-dontwarn com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
-dontwarn com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver
-dontwarn com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
-dontwarn com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
-dontwarn com.github.javaparser.utils.CollectionStrategy
-dontwarn com.github.javaparser.utils.ProjectRoot
-dontwarn com.github.javaparser.utils.SourceRoot
-dontwarn com.github.luben.zstd.BufferPool
-dontwarn com.github.luben.zstd.ZstdInputStream
-dontwarn com.github.luben.zstd.ZstdOutputStream
-dontwarn com.sun.org.apache.xml.internal.resolver.CatalogManager
-dontwarn com.sun.org.apache.xml.internal.resolver.tools.CatalogResolver
-dontwarn de.rototor.pdfbox.graphics2d.IPdfBoxGraphics2DFontTextDrawer$IFontTextDrawerEnv
-dontwarn de.rototor.pdfbox.graphics2d.IPdfBoxGraphics2DFontTextDrawer
-dontwarn de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D
-dontwarn de.rototor.pdfbox.graphics2d.PdfBoxGraphics2DFontTextDrawer
-dontwarn java.awt.AlphaComposite
-dontwarn java.awt.BasicStroke
-dontwarn java.awt.Canvas
-dontwarn java.awt.Color
-dontwarn java.awt.Component
-dontwarn java.awt.Composite
-dontwarn java.awt.Dimension
-dontwarn java.awt.Font
-dontwarn java.awt.FontFormatException
-dontwarn java.awt.FontMetrics
-dontwarn java.awt.GradientPaint
-dontwarn java.awt.Graphics2D
-dontwarn java.awt.Graphics
-dontwarn java.awt.GraphicsConfiguration
-dontwarn java.awt.GraphicsDevice
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.Image
-dontwarn java.awt.Insets
-dontwarn java.awt.LinearGradientPaint
-dontwarn java.awt.MediaTracker
-dontwarn java.awt.MultipleGradientPaint$ColorSpaceType
-dontwarn java.awt.MultipleGradientPaint$CycleMethod
-dontwarn java.awt.MultipleGradientPaint
-dontwarn java.awt.Paint
-dontwarn java.awt.PaintContext
-dontwarn java.awt.Polygon
-dontwarn java.awt.RadialGradientPaint
-dontwarn java.awt.Rectangle
-dontwarn java.awt.RenderingHints$Key
-dontwarn java.awt.RenderingHints
-dontwarn java.awt.Shape
-dontwarn java.awt.Stroke
-dontwarn java.awt.TexturePaint
-dontwarn java.awt.Toolkit
-dontwarn java.awt.color.ColorSpace
-dontwarn java.awt.font.FontRenderContext
-dontwarn java.awt.font.GlyphVector
-dontwarn java.awt.font.LineBreakMeasurer
-dontwarn java.awt.font.TextLayout
-dontwarn java.awt.geom.AffineTransform
-dontwarn java.awt.geom.Arc2D$Double
-dontwarn java.awt.geom.Arc2D$Float
-dontwarn java.awt.geom.Area
-dontwarn java.awt.geom.Dimension2D
-dontwarn java.awt.geom.Ellipse2D$Double
-dontwarn java.awt.geom.Ellipse2D$Float
-dontwarn java.awt.geom.GeneralPath
-dontwarn java.awt.geom.IllegalPathStateException
-dontwarn java.awt.geom.Line2D$Double
-dontwarn java.awt.geom.Line2D
-dontwarn java.awt.geom.NoninvertibleTransformException
-dontwarn java.awt.geom.Path2D$Double
-dontwarn java.awt.geom.Path2D
-dontwarn java.awt.geom.PathIterator
-dontwarn java.awt.geom.Point2D$Double
-dontwarn java.awt.geom.Point2D
-dontwarn java.awt.geom.Rectangle2D$Double
-dontwarn java.awt.geom.Rectangle2D$Float
-dontwarn java.awt.geom.Rectangle2D
-dontwarn java.awt.geom.RoundRectangle2D$Double
-dontwarn java.awt.geom.RoundRectangle2D$Float
-dontwarn java.awt.image.AffineTransformOp
-dontwarn java.awt.image.BufferedImage
-dontwarn java.awt.image.BufferedImageOp
-dontwarn java.awt.image.ColorModel
-dontwarn java.awt.image.ComponentColorModel
-dontwarn java.awt.image.DirectColorModel
-dontwarn java.awt.image.ImageObserver
-dontwarn java.awt.image.ImageProducer
-dontwarn java.awt.image.IndexColorModel
-dontwarn java.awt.image.MemoryImageSource
-dontwarn java.awt.image.PackedColorModel
-dontwarn java.awt.image.PixelGrabber
-dontwarn java.awt.image.Raster
-dontwarn java.awt.image.RenderedImage
-dontwarn java.awt.image.RescaleOp
-dontwarn java.awt.image.SampleModel
-dontwarn java.awt.image.WritableRaster
-dontwarn java.awt.image.renderable.RenderableImage
-dontwarn java.awt.print.PrinterGraphics
-dontwarn java.awt.print.PrinterJob
-dontwarn javax.imageio.IIOImage
-dontwarn javax.imageio.ImageIO
-dontwarn javax.imageio.ImageReadParam
-dontwarn javax.imageio.ImageReader
-dontwarn javax.imageio.ImageTypeSpecifier
-dontwarn javax.imageio.ImageWriteParam
-dontwarn javax.imageio.ImageWriter
-dontwarn javax.imageio.metadata.IIOMetadata
-dontwarn javax.imageio.plugins.jpeg.JPEGImageWriteParam
-dontwarn javax.imageio.stream.ImageInputStream
-dontwarn javax.imageio.stream.ImageOutputStream
-dontwarn javax.imageio.stream.MemoryCacheImageInputStream
-dontwarn javax.swing.JLabel
-dontwarn javax.xml.crypto.AlgorithmMethod
-dontwarn javax.xml.crypto.Data
-dontwarn javax.xml.crypto.KeySelector$Purpose
-dontwarn javax.xml.crypto.KeySelector
-dontwarn javax.xml.crypto.KeySelectorException
-dontwarn javax.xml.crypto.KeySelectorResult
-dontwarn javax.xml.crypto.MarshalException
-dontwarn javax.xml.crypto.OctetStreamData
-dontwarn javax.xml.crypto.URIDereferencer
-dontwarn javax.xml.crypto.URIReference
-dontwarn javax.xml.crypto.URIReferenceException
-dontwarn javax.xml.crypto.XMLCryptoContext
-dontwarn javax.xml.crypto.XMLStructure
-dontwarn javax.xml.crypto.dom.DOMCryptoContext
-dontwarn javax.xml.crypto.dom.DOMStructure
-dontwarn javax.xml.crypto.dsig.CanonicalizationMethod
-dontwarn javax.xml.crypto.dsig.DigestMethod
-dontwarn javax.xml.crypto.dsig.Manifest
-dontwarn javax.xml.crypto.dsig.Reference
-dontwarn javax.xml.crypto.dsig.SignatureMethod
-dontwarn javax.xml.crypto.dsig.SignatureProperties
-dontwarn javax.xml.crypto.dsig.SignatureProperty
-dontwarn javax.xml.crypto.dsig.SignedInfo
-dontwarn javax.xml.crypto.dsig.Transform
-dontwarn javax.xml.crypto.dsig.TransformException
-dontwarn javax.xml.crypto.dsig.TransformService
-dontwarn javax.xml.crypto.dsig.XMLObject
-dontwarn javax.xml.crypto.dsig.XMLSignContext
-dontwarn javax.xml.crypto.dsig.XMLSignature
-dontwarn javax.xml.crypto.dsig.XMLSignatureException
-dontwarn javax.xml.crypto.dsig.XMLSignatureFactory
-dontwarn javax.xml.crypto.dsig.XMLValidateContext
-dontwarn javax.xml.crypto.dsig.dom.DOMSignContext
-dontwarn javax.xml.crypto.dsig.dom.DOMValidateContext
-dontwarn javax.xml.crypto.dsig.keyinfo.KeyInfo
-dontwarn javax.xml.crypto.dsig.keyinfo.KeyInfoFactory
-dontwarn javax.xml.crypto.dsig.keyinfo.KeyValue
-dontwarn javax.xml.crypto.dsig.keyinfo.X509Data
-dontwarn javax.xml.crypto.dsig.keyinfo.X509IssuerSerial
-dontwarn javax.xml.crypto.dsig.spec.C14NMethodParameterSpec
-dontwarn javax.xml.crypto.dsig.spec.DigestMethodParameterSpec
-dontwarn javax.xml.crypto.dsig.spec.SignatureMethodParameterSpec
-dontwarn javax.xml.crypto.dsig.spec.TransformParameterSpec
-dontwarn javax.xml.crypto.dsig.spec.XPathFilter2ParameterSpec
-dontwarn javax.xml.crypto.dsig.spec.XPathType$Filter
-dontwarn javax.xml.crypto.dsig.spec.XPathType
-dontwarn javax.xml.stream.Location
-dontwarn javax.xml.stream.XMLEventFactory
-dontwarn javax.xml.stream.XMLInputFactory
-dontwarn javax.xml.stream.XMLOutputFactory
-dontwarn javax.xml.stream.XMLStreamException
-dontwarn javax.xml.stream.XMLStreamReader
-dontwarn javax.xml.stream.XMLStreamWriter
-dontwarn javax.xml.stream.events.Namespace
-dontwarn javax.xml.stream.util.StreamReaderDelegate
-dontwarn net.sf.saxon.Configuration
-dontwarn net.sf.saxon.dom.DOMNodeWrapper
-dontwarn net.sf.saxon.dom.DocumentWrapper
-dontwarn net.sf.saxon.dom.NodeOverNodeInfo
-dontwarn net.sf.saxon.lib.ConversionRules
-dontwarn net.sf.saxon.ma.map.HashTrieMap
-dontwarn net.sf.saxon.om.GroundedValue
-dontwarn net.sf.saxon.om.Item
-dontwarn net.sf.saxon.om.NamespaceUri
-dontwarn net.sf.saxon.om.NodeInfo
-dontwarn net.sf.saxon.om.Sequence
-dontwarn net.sf.saxon.om.SequenceTool
-dontwarn net.sf.saxon.om.StructuredQName
-dontwarn net.sf.saxon.query.DynamicQueryContext
-dontwarn net.sf.saxon.query.StaticQueryContext
-dontwarn net.sf.saxon.query.XQueryExpression
-dontwarn net.sf.saxon.str.StringView
-dontwarn net.sf.saxon.str.UnicodeString
-dontwarn net.sf.saxon.sxpath.IndependentContext
-dontwarn net.sf.saxon.sxpath.XPathDynamicContext
-dontwarn net.sf.saxon.sxpath.XPathEvaluator
-dontwarn net.sf.saxon.sxpath.XPathExpression
-dontwarn net.sf.saxon.sxpath.XPathStaticContext
-dontwarn net.sf.saxon.sxpath.XPathVariable
-dontwarn net.sf.saxon.tree.wrapper.VirtualNode
-dontwarn net.sf.saxon.type.BuiltInAtomicType
-dontwarn net.sf.saxon.type.ConversionResult
-dontwarn net.sf.saxon.value.AnyURIValue
-dontwarn net.sf.saxon.value.AtomicValue
-dontwarn net.sf.saxon.value.BigDecimalValue
-dontwarn net.sf.saxon.value.BigIntegerValue
-dontwarn net.sf.saxon.value.BooleanValue
-dontwarn net.sf.saxon.value.CalendarValue
-dontwarn net.sf.saxon.value.DateTimeValue
-dontwarn net.sf.saxon.value.DateValue
-dontwarn net.sf.saxon.value.DoubleValue
-dontwarn net.sf.saxon.value.DurationValue
-dontwarn net.sf.saxon.value.FloatValue
-dontwarn net.sf.saxon.value.GDateValue
-dontwarn net.sf.saxon.value.GDayValue
-dontwarn net.sf.saxon.value.GMonthDayValue
-dontwarn net.sf.saxon.value.GMonthValue
-dontwarn net.sf.saxon.value.GYearMonthValue
-dontwarn net.sf.saxon.value.GYearValue
-dontwarn net.sf.saxon.value.HexBinaryValue
-dontwarn net.sf.saxon.value.Int64Value
-dontwarn net.sf.saxon.value.ObjectValue
-dontwarn net.sf.saxon.value.QNameValue
-dontwarn net.sf.saxon.value.SaxonDuration
-dontwarn net.sf.saxon.value.SaxonXMLGregorianCalendar
-dontwarn net.sf.saxon.value.StringValue
-dontwarn net.sf.saxon.value.TimeValue
-dontwarn org.apache.batik.anim.dom.SAXSVGDocumentFactory
-dontwarn org.apache.batik.bridge.BridgeContext
-dontwarn org.apache.batik.bridge.DocumentLoader
-dontwarn org.apache.batik.bridge.GVTBuilder
-dontwarn org.apache.batik.bridge.UserAgent
-dontwarn org.apache.batik.bridge.UserAgentAdapter
-dontwarn org.apache.batik.bridge.ViewBox
-dontwarn org.apache.batik.dom.GenericDOMImplementation
-dontwarn org.apache.batik.ext.awt.RenderingHintsKeyExt
-dontwarn org.apache.batik.ext.awt.image.renderable.ClipRable8Bit
-dontwarn org.apache.batik.ext.awt.image.renderable.ClipRable
-dontwarn org.apache.batik.ext.awt.image.renderable.Filter
-dontwarn org.apache.batik.gvt.GraphicsNode
-dontwarn org.apache.batik.parser.DefaultLengthHandler
-dontwarn org.apache.batik.parser.LengthHandler
-dontwarn org.apache.batik.parser.LengthParser
-dontwarn org.apache.batik.svggen.DOMTreeManager
-dontwarn org.apache.batik.svggen.DefaultExtensionHandler
-dontwarn org.apache.batik.svggen.ExtensionHandler
-dontwarn org.apache.batik.svggen.SVGColor
-dontwarn org.apache.batik.svggen.SVGGeneratorContext$GraphicContextDefaults
-dontwarn org.apache.batik.svggen.SVGGeneratorContext
-dontwarn org.apache.batik.svggen.SVGGraphics2D
-dontwarn org.apache.batik.svggen.SVGIDGenerator
-dontwarn org.apache.batik.svggen.SVGPaintDescriptor
-dontwarn org.apache.batik.svggen.SVGTexturePaint
-dontwarn org.apache.batik.util.XMLResourceDescriptor
-dontwarn org.apache.jcp.xml.dsig.internal.dom.ApacheNodeSetData
-dontwarn org.apache.jcp.xml.dsig.internal.dom.DOMKeyInfo
-dontwarn org.apache.jcp.xml.dsig.internal.dom.DOMKeyInfoFactory
-dontwarn org.apache.jcp.xml.dsig.internal.dom.DOMReference
-dontwarn org.apache.jcp.xml.dsig.internal.dom.DOMSignedInfo
-dontwarn org.apache.jcp.xml.dsig.internal.dom.DOMSubTreeData
-dontwarn org.apache.jcp.xml.dsig.internal.dom.DOMUtils
-dontwarn org.apache.jcp.xml.dsig.internal.dom.DOMXMLSignature
-dontwarn org.apache.jcp.xml.dsig.internal.dom.XMLDSigRI
-dontwarn org.apache.maven.model.Resource
-dontwarn org.apache.maven.plugin.AbstractMojo
-dontwarn org.apache.maven.plugin.MojoExecutionException
-dontwarn org.apache.maven.plugin.MojoFailureException
-dontwarn org.apache.maven.plugin.logging.Log
-dontwarn org.apache.maven.plugins.annotations.LifecyclePhase
-dontwarn org.apache.maven.plugins.annotations.Mojo
-dontwarn org.apache.maven.plugins.annotations.Parameter
-dontwarn org.apache.maven.project.MavenProject
-dontwarn org.apache.pdfbox.pdmodel.PDDocument
-dontwarn org.apache.pdfbox.pdmodel.PDPage
-dontwarn org.apache.pdfbox.pdmodel.PDPageContentStream
-dontwarn org.apache.pdfbox.pdmodel.common.PDRectangle
-dontwarn org.apache.pdfbox.pdmodel.font.PDFont
-dontwarn org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
-dontwarn org.apache.tools.ant.BuildException
-dontwarn org.apache.tools.ant.DirectoryScanner
-dontwarn org.apache.tools.ant.FileScanner
-dontwarn org.apache.tools.ant.Project
-dontwarn org.apache.tools.ant.taskdefs.Jar
-dontwarn org.apache.tools.ant.taskdefs.Javac
-dontwarn org.apache.tools.ant.taskdefs.MatchingTask
-dontwarn org.apache.tools.ant.types.FileSet
-dontwarn org.apache.tools.ant.types.Path$PathElement
-dontwarn org.apache.tools.ant.types.Path
-dontwarn org.apache.tools.ant.types.Reference
-dontwarn org.apache.xml.security.Init
-dontwarn org.apache.xml.security.c14n.Canonicalizer
-dontwarn org.apache.xml.security.signature.XMLSignatureInput
-dontwarn org.apache.xml.security.utils.Base64
-dontwarn org.apache.xml.security.utils.XMLUtils
-dontwarn org.bouncycastle.asn1.ASN1IA5String
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.brotli.dec.BrotliInputStream
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.Oid
-dontwarn org.objectweb.asm.AnnotationVisitor
-dontwarn org.objectweb.asm.Attribute
-dontwarn org.objectweb.asm.ClassReader
-dontwarn org.objectweb.asm.ClassVisitor
-dontwarn org.objectweb.asm.FieldVisitor
-dontwarn org.objectweb.asm.Label
-dontwarn org.objectweb.asm.MethodVisitor
-dontwarn org.objectweb.asm.Type
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn org.osgi.framework.Bundle
-dontwarn org.osgi.framework.BundleContext
-dontwarn org.osgi.framework.FrameworkUtil
-dontwarn org.osgi.framework.ServiceReference
-dontwarn org.tukaani.xz.ARMOptions
-dontwarn org.tukaani.xz.ARMThumbOptions
-dontwarn org.tukaani.xz.DeltaOptions
-dontwarn org.tukaani.xz.FilterOptions
-dontwarn org.tukaani.xz.FinishableOutputStream
-dontwarn org.tukaani.xz.FinishableWrapperOutputStream
-dontwarn org.tukaani.xz.IA64Options
-dontwarn org.tukaani.xz.LZMA2InputStream
-dontwarn org.tukaani.xz.LZMA2Options
-dontwarn org.tukaani.xz.LZMAInputStream
-dontwarn org.tukaani.xz.LZMAOutputStream
-dontwarn org.tukaani.xz.MemoryLimitException
-dontwarn org.tukaani.xz.PowerPCOptions
-dontwarn org.tukaani.xz.SPARCOptions
-dontwarn org.tukaani.xz.SingleXZInputStream
-dontwarn org.tukaani.xz.UnsupportedOptionsException
-dontwarn org.tukaani.xz.X86Options
-dontwarn org.tukaani.xz.XZ
-dontwarn org.tukaani.xz.XZInputStream
-dontwarn org.tukaani.xz.XZOutputStream
-dontwarn org.w3c.dom.events.Event
-dontwarn org.w3c.dom.events.EventListener
-dontwarn org.w3c.dom.events.EventTarget
-dontwarn org.w3c.dom.events.MutationEvent
-dontwarn org.w3c.dom.svg.SVGDocument
-dontwarn org.w3c.dom.svg.SVGSVGElement
-dontwarn org.w3c.dom.traversal.DocumentTraversal
-dontwarn org.w3c.dom.traversal.NodeFilter
-dontwarn org.w3c.dom.traversal.NodeIterator