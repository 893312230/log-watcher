package com.smartops.domain.runbook.port;

import com.smartops.domain.runbook.Runbook;

import java.util.List;
import java.util.Optional;

/**
 * Runbook 定义持久化端口。
 *
 * <p>实现位于 infrastructure 层（JPA），保存时级联替换步骤列表。</p>
 */
public interface RunbookRepository {

    /**
     * 保存（新建或更新）Runbook，步骤列表整体替换。
     *
     * @param runbook Runbook 定义（id 为 null 表示新建）
     * @return 含分配 id 的 Runbook
     */
    Runbook save(Runbook runbook);

    /**
     * 按 id 查询。
     *
     * @param id 主键
     * @return Runbook 或空
     */
    Optional<Runbook> findById(long id);

    /**
     * 查询全部 Runbook（按 id 升序）。
     *
     * @return Runbook 列表
     */
    List<Runbook> findAll();

    /**
     * 按 id 删除（含步骤）。
     *
     * @param id 主键
     */
    void deleteById(long id);
}
