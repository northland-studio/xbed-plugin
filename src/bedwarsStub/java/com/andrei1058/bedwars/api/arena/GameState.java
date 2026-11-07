/*
 * XBedSync 编译存根（compile-only stub）
 *
 * 本目录只包含 XBedSync 实际使用的 BedWars1058 23.1.1 公开 API 的最小签名副本。
 * 用途：当构建环境没有 libs/Bedwars-plugin-23.1.1.jar 时（例如公开 GitHub 仓库的 CI），
 *      让插件源码仍然可以编译。存根不参与打 jar，运行期使用的是真实 BedWars1058 插件。
 *
 * 所有签名均通过 javap 对 Bedwars-plugin-23.1.1.jar 核对，详见 docs/DATA-MAPPING.md。
 * 若升级 BedWars1058，请重新用 javap 核对并通过 libs/ 真实 jar 构建验证。
 */
package com.andrei1058.bedwars.api.arena;

public enum GameState {
    waiting,
    starting,
    playing,
    restarting
}
