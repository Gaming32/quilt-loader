/*
 * Copyright 2016 FabricMC
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

package org.quiltmc.loader.impl.game.minecraft;

import net.fabricmc.api.EnvType;
import org.quiltmc.loader.impl.entrypoint.GameTransformer;
import org.quiltmc.loader.impl.game.minecraft.patch.BrandingPatch;
import org.quiltmc.loader.impl.game.minecraft.patch.EntrypointPatch;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.game.GameProviderHelper;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.metadata.BuiltinModMetadata;
import org.quiltmc.loader.impl.metadata.ModDependencyImpl;
import org.quiltmc.loader.impl.util.Arguments;
import org.quiltmc.loader.impl.util.SystemProperties;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import org.quiltmc.loader.impl.util.log.Log;

public class MinecraftGameProvider implements GameProvider {
	private EnvType envType;
	private String entrypoint;
	private Arguments arguments;
	private Path gameJar, realmsJar;
	private McVersion versionData;
	private boolean hasModLoader = false;

	public static final GameTransformer TRANSFORMER = new GameTransformer(it -> Arrays.asList(
			new EntrypointPatch(it),
			new BrandingPatch(it)
			));

	@Override
	public String getGameId() {
		return "minecraft";
	}

	@Override
	public String getGameName() {
		return "Minecraft";
	}

	@Override
	public String getRawGameVersion() {
		return versionData.getRaw();

	}

	@Override
	public String getNormalizedGameVersion() {
		return versionData.getNormalized();
	}

	@Override
	public Collection<BuiltinMod> getBuiltinMods() {
		URL url;
		try {
			url = gameJar.toUri().toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}

		BuiltinModMetadata.Builder metadata = new BuiltinModMetadata.Builder(getGameId(), getNormalizedGameVersion())
				.setName(getGameName());

		if (versionData.getClassVersion().isPresent()) {
			int version = versionData.getClassVersion().getAsInt() - 44;
			metadata.addDepends(new ModDependencyImpl("java", Collections.singletonList(String.format(">=%d", version))));
		}

		return Arrays.asList(new BuiltinMod(url, metadata.build()));
	}

	public Path getGameJar() {
		return gameJar;
	}

	@Override
	public String getEntrypoint() {
		return entrypoint;
	}

	@Override
	public Path getLaunchDirectory() {
		if (arguments == null) {
			return new File(".").toPath();
		}

		return QuiltLauncherBase.getLaunchDirectory(arguments).toPath();
	}

	@Override
	public boolean isObfuscated() {
		return true; // generally yes...
	}

	@Override
	public boolean requiresUrlClassLoader() {
		return hasModLoader;
	}

	@Override
	public List<Path> getGameContextJars() {
		List<Path> list = new ArrayList<>();
		list.add(gameJar);

		if (realmsJar != null) {
			list.add(realmsJar);
		}

		return list;
	}

	@Override
	public boolean locateGame(EnvType envType, String[] args, ClassLoader loader) {
		this.envType = envType;
		this.arguments = new Arguments();
		arguments.parse(args);

		List<String> entrypointClasses;

		if (envType == EnvType.CLIENT) {
			entrypointClasses = Arrays.asList("net.minecraft.client.main.Main", "net.minecraft.client.MinecraftApplet", "com.mojang.minecraft.MinecraftApplet");
		} else {
			entrypointClasses = Arrays.asList("net.minecraft.server.Main", "net.minecraft.server.MinecraftServer", "com.mojang.minecraft.server.MinecraftServer");
		}

		Optional<GameProviderHelper.EntrypointResult> entrypointResult = GameProviderHelper.findFirstClass(loader, entrypointClasses);

		if (!entrypointResult.isPresent()) {
			return false;
		}

		Log.init(new Log4jLogHandler(), true);

		entrypoint = entrypointResult.get().entrypointName;
		gameJar = entrypointResult.get().entrypointPath;
		realmsJar = GameProviderHelper.getSource(loader, "realmsVersion").orElse(null);
		hasModLoader = GameProviderHelper.getSource(loader, "ModLoader.class").isPresent();

		String version = arguments.remove(Arguments.GAME_VERSION);
		if (version == null) version = System.getProperty(SystemProperties.GAME_VERSION);
		versionData = McVersionLookup.getVersion(gameJar, entrypointClasses, version);

		QuiltLauncherBase.processArgumentMap(arguments, envType);

		return true;
	}

	@Override
	public String[] getLaunchArguments(boolean sanitize) {
		if (arguments != null) {
			List<String> list = new ArrayList<>(Arrays.asList(arguments.toArray()));

			if (sanitize) {
				int remove = 0;
				Iterator<String> iterator = list.iterator();

				while (iterator.hasNext()) {
					String next = iterator.next();

					if ("--accessToken".equals(next)) {
						remove = 2;
					}

					if (remove > 0) {
						iterator.remove();
						remove--;
					}
				}
			}

			return list.toArray(new String[0]);
		}

		return new String[0];
	}

	@Override
	public GameTransformer getEntrypointTransformer() {
		return TRANSFORMER;
	}

	@Override
	public boolean canOpenErrorGui() {
		if (arguments == null || envType == EnvType.CLIENT) {
			return true;
		}

		List<String> extras = arguments.getExtraArgs();
		return !extras.contains("nogui") && !extras.contains("--nogui");
	}

	@Override
	public void launch(ClassLoader loader) {
		String targetClass = entrypoint;

		if (envType == EnvType.CLIENT && targetClass.contains("Applet")) {
			targetClass = "org.quiltmc.loader.impl.entrypoint.applet.AppletMain";
		}

		try {
			Class<?> c = loader.loadClass(targetClass);
			Method m = c.getMethod("main", String[].class);
			m.invoke(null, (Object) arguments.toArray());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}