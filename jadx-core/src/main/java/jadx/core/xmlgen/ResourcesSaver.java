package jadx.core.xmlgen;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.api.JadxDecompiler;
import jadx.api.ResourceFile;
import jadx.api.ResourcesLoader;
import jadx.core.codegen.CodeWriter;
import jadx.core.utils.exceptions.JadxException;
import jadx.core.utils.exceptions.JadxRuntimeException;
import jadx.core.utils.files.FileUtils;
import jadx.core.utils.files.ZipSecurity;

public class ResourcesSaver implements Runnable {
	private static final Logger LOG = LoggerFactory.getLogger(ResourcesSaver.class);

	private final ResourceFile resourceFile;
	private File outDir;
	private JadxDecompiler.ReverseCallBack reverseCallBack;

	public ResourcesSaver(File outDir, ResourceFile resourceFile, JadxDecompiler.ReverseCallBack reverseCallBack) {
		this.resourceFile = resourceFile;
		this.outDir = outDir;
		this.reverseCallBack=reverseCallBack;
	}

	@Override
	public void run() {
		saveResources(resourceFile.loadContent());
	}

	private void saveResources(ResContainer rc) {
		if (rc == null) {
			return;
		}
		if (rc.getDataType() == ResContainer.DataType.RES_TABLE) {
			saveToFile(rc, new File(outDir, "res/values/public.xml"));
			for (ResContainer subFile : rc.getSubFiles()) {
				saveResources(subFile);
			}
		} else {
			save(rc, outDir);
		}
	}

	private void save(ResContainer rc, File outDir) {
		File outFile = new File(outDir, rc.getFileName());
		if (!ZipSecurity.isInSubDirectory(outDir, outFile)) {
			LOG.error("Path traversal attack detected, invalid resource name: {}", outFile.getPath());
			return;
		}
		if (reverseCallBack!=null) reverseCallBack.currentFile("Saving resource :\n"+resourceFile.getName());
		saveToFile(rc, outFile);
	}

	private void saveToFile(ResContainer rc, File outFile) {
		switch (rc.getDataType()) {
			case TEXT:
			case RES_TABLE:
				CodeWriter cw = rc.getText();
				cw.save(outFile);
				return;

			case DECODED_DATA:
				byte[] data = rc.getDecodedData();
				FileUtils.makeDirsForFile(outFile);
				try {
					Files.write(outFile.toPath(), data);
				} catch (Exception e) {
					LOG.warn("Resource '{}' not saved, got exception", rc.getName(), e);
				}
				return;

			case RES_LINK:
				ResourceFile resFile = rc.getResLink();
				FileUtils.makeDirsForFile(outFile);
				try {
					saveResourceFile(resFile, outFile);
				} catch (Exception e) {
					LOG.warn("Resource '{}' not saved, got exception", rc.getName(), e);
				}
				return;

			default:
				LOG.warn("Resource '{}' not saved, unknown type", rc.getName());
				break;
		}
	}

	private void saveResourceFile(ResourceFile resFile, File outFile) throws JadxException {
		ResourcesLoader.decodeStream(resFile, (size, is) -> {
			try {
				Files.copy(is, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception e) {
				throw new JadxRuntimeException("Resource file save error", e);
			}
			return null;
		});
	}
}
