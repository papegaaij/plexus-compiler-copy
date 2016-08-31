package org.codehaus.plexus.compiler.copy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The MIT License
 *
 * Copyright (c) 2005, The Codehaus
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import org.codehaus.plexus.compiler.AbstractCompiler;
import org.codehaus.plexus.compiler.CompilerConfiguration;
import org.codehaus.plexus.compiler.CompilerException;
import org.codehaus.plexus.compiler.CompilerMessage;
import org.codehaus.plexus.compiler.CompilerMessage.Kind;
import org.codehaus.plexus.compiler.CompilerOutputStyle;
import org.codehaus.plexus.compiler.CompilerResult;
import org.codehaus.plexus.util.DirectoryScanner;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * @plexus.component role="org.codehaus.plexus.compiler.Compiler"
 *                   role-hint="copy"
 */
public class CopyJavaCompiler extends AbstractCompiler {
	public CopyJavaCompiler() {
		super(CompilerOutputStyle.ONE_OUTPUT_FILE_PER_INPUT_FILE, ".java", ".class", null);
	}

	public CompilerResult performCompile(CompilerConfiguration config) throws CompilerException {

		Path sourceBase = Paths.get(config.getCustomCompilerArgumentsAsMap().get("-sourcePath"));
		Path workDir = config.getWorkingDirectory().toPath();
		Path thisBase = Paths.get(workDir.getFileSystem().getSeparator());
		for (Path segment : workDir) {
			thisBase = thisBase.resolve(segment);
			if (segment.getFileName().equals(sourceBase.getFileName()))
				break;
		}
		CompilerResult compilerResult = new CompilerResult();
		Map<String, List<Path>> classesPerSource = new HashMap<String, List<Path>>();
		Set<Path> classFiles = getClassFilesForDirectory(config.getCustomCompilerArgumentsAsMap().get("-sourcePath"));
		for (Path curClassFile : classFiles) {
			FileInputStream inputStream = null;
			try {
				inputStream = new FileInputStream(curClassFile.toFile());
				ClassReader reader = new ClassReader(inputStream);
				RecordInfoVisitor visitor = new RecordInfoVisitor();
				reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
				String sourcePath = visitor.getClassName().substring(0, visitor.getClassName().lastIndexOf('/') + 1)
						+ visitor.getSourceFile();
				List<Path> curPaths = classesPerSource.get(sourcePath);
				if (curPaths == null) {
					curPaths = new ArrayList<Path>();
					classesPerSource.put(sourcePath, curPaths);
				}
				curPaths.add(curClassFile);
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				try {
					inputStream.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
		Path target = Paths.get(config.getOutputLocation());
		for (String sourceRoot : config.getSourceLocations()) {
			// annotations directory does not always exist and the below scanner
			// fails on non existing directories
			File potentialSourceDirectory = new File(sourceRoot);
			if (potentialSourceDirectory.exists()) {
				Set<String> sources = getSourceFilesForSourceRoot(config, sourceRoot);

				for (String source : sources) {
					try {
						Path curSourcePath = potentialSourceDirectory.toPath().relativize(Paths.get(source));

						Set<Path> done = new HashSet<Path>();
						List<Path> classes = classesPerSource.get(curSourcePath.toString());
						Path curTargetDir = target.resolve(curSourcePath.getParent());
						Files.createDirectories(curTargetDir);
						for (Path curClass : classes) {
							compilerResult.getCompilerMessages()
									.add(new CompilerMessage("Compiling " + curSourcePath + ": " + curClass + " -> "
											+ curTargetDir.resolve(curClass.getFileName()), Kind.NOTE));
							if (!done.add(curClass.getFileName())) {
								throw new CompilerException(
										curSourcePath + " resolves to multiple classes with the same name: " + classes);
							}
							Files.copy(curClass, curTargetDir.resolve(curClass.getFileName()),
									StandardCopyOption.REPLACE_EXISTING);
						}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			}
		}
		return compilerResult;
	}

	private static class RecordInfoVisitor extends ClassVisitor {
		private String className;
		private String sourceFile;

		public RecordInfoVisitor() {
			super(Opcodes.ASM5);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName,
				String[] interfaces) {
			className = name;
		}

		@Override
		public void visitSource(String source, String debug) {
			this.sourceFile = source;
		}

		public String getClassName() {
			return className;
		}

		public String getSourceFile() {
			return sourceFile;
		}
	}

	protected static Set<Path> getClassFilesForDirectory(String directory) {
		DirectoryScanner scanner = new DirectoryScanner();

		scanner.setBasedir(directory);
		scanner.setIncludes(new String[] { "**/*.class" });
		scanner.scan();

		Set<Path> classes = new HashSet<Path>();
		for (String sourceDirectorySource : scanner.getIncludedFiles()) {
			classes.add(Paths.get(directory, sourceDirectorySource));
		}
		return classes;
	}

	public String[] createCommandLine(CompilerConfiguration config) throws CompilerException {
		return null;
	}
}
