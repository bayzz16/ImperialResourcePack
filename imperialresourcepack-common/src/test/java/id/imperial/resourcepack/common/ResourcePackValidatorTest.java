package id.imperial.resourcepack.common;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
class ResourcePackValidatorTest {
 @Test void acceptsPackWithMcmeta() throws Exception {Path f=Files.createTempFile("pack",".zip");try(ZipOutputStream z=new ZipOutputStream(Files.newOutputStream(f))){z.putNextEntry(new ZipEntry("pack.mcmeta"));z.write("{}".getBytes());z.closeEntry();}assertTrue(new ResourcePackValidator().validate(f,true,100000).valid());}
 @Test void rejectsTraversal() throws Exception {Path f=Files.createTempFile("pack",".zip");try(ZipOutputStream z=new ZipOutputStream(Files.newOutputStream(f))){z.putNextEntry(new ZipEntry("../secret"));z.write(1);z.closeEntry();}assertFalse(new ResourcePackValidator().validate(f,false,100000).valid());}
}
