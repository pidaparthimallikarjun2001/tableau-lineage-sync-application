package com.example.tableau.repository;

import com.example.tableau.entity.TableauSite;
import com.example.tableau.enums.StatusFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableauSiteRepository extends JpaRepository<TableauSite, Long> {

    Optional<TableauSite> findByAssetId(String assetId);

    Optional<TableauSite> findByContentUrl(String contentUrl);

    List<TableauSite> findByServerId(Long serverId);

    List<TableauSite> findByStatusFlag(StatusFlag statusFlag);

    List<TableauSite> findByStatusFlagNot(StatusFlag statusFlag);

    @Modifying
    @Query("UPDATE TableauSite s SET s.statusFlag = :statusFlag WHERE s.assetId = :assetId")
    int updateStatusFlagByAssetId(@Param("assetId") String assetId, @Param("statusFlag") StatusFlag statusFlag);

    @Modifying
    @Query("UPDATE TableauSite s SET s.statusFlag = :statusFlag WHERE s.server.id = :serverId")
    int updateStatusFlagByServerId(@Param("serverId") Long serverId, @Param("statusFlag") StatusFlag statusFlag);

    @Query("SELECT s FROM TableauSite s WHERE s.statusFlag != 'DELETED'")
    List<TableauSite> findAllActive();

    boolean existsByAssetId(String assetId);
}
