/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugin.pgp.signing

import org.bouncycastle.bcpg.BCPGOutputStream
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureGenerator

import java.security.Security

/**
 * Performs the task of signing data with a given key.
 */
class PgpSigner {
	
	private static final String PROVIDER = "BC"
	
	private final PGPSecretKey secretKey
	private final String password
	
	PgpSigner(PGPSecretKey secretKey, String password) {
		this.secretKey = secretKey
		this.password = password
	}
	
	/**
	 * Creates signature files alongside the input file of the specified output types.
	 * 
	 * @return The created files indexed by the output type
	 */
	Map<OutputType, File> sign(File input, OutputType[] outputTypes) {
		def signature = input.withInputStream { sign(it) }
		def files = [:]
		
		for (outputType in outputTypes) {
			def signatureFile = new File(input.absolutePath + ".$outputType.fileExtension")
			signatureFile.withOutputStream { outputType.write(signature, it) }
			files[outputType] = signatureFile
		}
	}
	
	/**
	 * Writes the signature for the bytes on {@code input} to {@code output}.
	 * 
	 * The caller is responsible for closing the streams, though the output WILL be flushed.
	 */
	void sign(InputStream input, OutputStream output) {
		// ok to call multiple times, will be ignored
		Security.addProvider(new BouncyCastleProvider())
		
		def privateKey = secretKey.extractPrivateKey(password.toCharArray(), PROVIDER)
		def signatureGenerator = new PGPSignatureGenerator(secretKey.publicKey.algorithm, PGPUtil.SHA1, "BC")
		
		signatureGenerator.initSign(PGPSignature.BINARY_DOCUMENT, privateKey)

		def buffer = new byte[1024]
		def read = input.read(buffer)
		while (read > 0) {
			signatureGenerator.update(buffer, 0, read)
			read = input.read(buffer)
		}

		// BCPGOutputStream seems to do some internal buffering, it's unclear whether it's stricly required here though
		def bufferedOutput = new BCPGOutputStream(output)
		signatureGenerator.generate().encode(bufferedOutput)
		bufferedOutput.flush()
	}
	
	byte[] sign(InputStream input) {
		def output = new ByteArrayOutputStream()
		sign(input, output)
		output.toByteArray()
	}
	
	/**
	 * A kind of external representation of a signature.
	 */
	static enum OutputType {
		BINARY("sig"),
		ARMORED("asc", { new ArmoredOutputStream(it) })

		final String fileExtension
		private Closure outputDecorator
		
		OutputType(String fileExtension, Closure outputDecorator = { it }) {
			this.fileExtension = fileExtension
			this.outputDecorator = outputDecorator
		}
		
		void write(byte[] bytes, OutputStream output) {
			outputDecorator(output) << bytes
		}
		
		void write(InputStream input, OutputStream output) {
			outputDecorator(output) << input
		}
		
		// nicer name
		static OutputType[] getAll() {
			OutputType.values()
		}
	}
}