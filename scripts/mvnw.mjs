#!/usr/bin/env node
/**
 * Runs the backend Maven Wrapper from an npm script on any platform.
 *
 * npm scripts execute through cmd.exe on Windows, where `./mvnw` is not runnable — the
 * wrapper ships as two files and the right one has to be picked per platform. The path is
 * resolved absolutely because cmd.exe looks a bare `mvnw.cmd` up on PATH rather than in the
 * working directory.
 */
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const backendDir = path.join(projectRoot, 'backend')
const isWindows = process.platform === 'win32'
const wrapper = path.join(backendDir, isWindows ? 'mvnw.cmd' : 'mvnw')

// A .cmd file is not an executable image, so on Windows cmd.exe has to run it. Invoking cmd
// explicitly rather than through spawn's `shell: true` keeps the arguments as an array, so
// nothing needs hand escaping.
const command = isWindows ? process.env.COMSPEC ?? 'cmd.exe' : wrapper
const args = isWindows ? ['/d', '/s', '/c', wrapper, ...process.argv.slice(2)] : process.argv.slice(2)

const child = spawn(command, args, {
  cwd: backendDir,
  stdio: 'inherit',
})

child.on('exit', (code) => process.exit(code ?? 1))
child.on('error', (error) => {
  console.error(`Không chạy được Maven Wrapper: ${error.message}`)
  process.exit(1)
})
